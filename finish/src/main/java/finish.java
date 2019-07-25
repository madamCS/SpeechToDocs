import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.Request;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.protobuf.ByteString;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.http.client.CredentialsProvider;

/**
 * Outputs the transcript of an audio file into a 
 * newly created Google Document.
 */
public class CreateTranscript {
  private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
  
  // Specify audio file name below.
  private static final String AUDIO_FILE = "audiofile.wav";
  private static final String TOKENS_DIRECTORY_PATH = "tokens";
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final String APPLICATION_NAME = "CreateTranscript";
  private static final List<String> SCOPES = Collections.singletonList(DocsScopes.DOCUMENTS);

  public static void main(String args[]) throws IOException, GeneralSecurityException {
      final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      Docs service = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
          .setApplicationName(APPLICATION_NAME)
          .build();

      createTranscript(service, AUDIO_FILE);
  }

  /**
   * Creates an authorized Credential object.
   *
   * @param HTTP_TRANSPORT The network HTTP Transport.
   * @return An authorized Credential object.
   * @throws IOException If the credentials.json file cannot be found.
   */
  static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
    InputStream in = CreateTranscript.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
    if (in == null) {
      throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
    }

    GoogleClientSecrets clientSecrets =
        GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();

    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
    System.out.println(receiver);
    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
  }

  /**
   * Calls helper functions to create Doc, get audio file's transcript, and insert transcript into
   * created Doc.
   *
   * @param {Object} service Docs authorization service to be able to use the Docs API.
   * @param {String} AUDIO_FILE File name of the audio file sending to the Speech-to-Text API.
   */
  private static void createTranscript(Docs service, String AUDIO_FILE) throws IOException {
      String docId = createDocument(service);
      getTranscript(service, AUDIO_FILE, docId);
  }

  /**
   * Creates a new Google Document. Once the document is created, returns its Document ID.
   * 
   * @param {Object} service Docs authorized service to be able to create a Doc.
   * @return {String} Returns the Document ID of the newly created Doc.
   */
  public static String createDocument(Docs service) throws IOException {
    Document doc = new Document().setTitle("Transcript for " + AUDIO_FILE);
    doc = service.documents().create(doc).execute();
    String documentId = doc.getDocumentId();
    return documentId;
  }

  /**
   * Obtains the transcript of an audio file by calling the Google Speech-to-Text API.
   *
   * @param {Object} service Docs authorized service to be able to pass through in the 
   * insertText() function call.
   * @param {String} audioFile The name of the audio file.
   * @param {String} docID Document ID of the Doc you'd like to write to.
   */
  public static void getTranscript(Docs service, String audioFile, String docId) throws IOException {
    SpeechClient speech = SpeechClient.create();
    Path path = Paths.get(audioFile);
    System.out.println("Audio file path is: " + path);
    byte[] data = Files.readAllBytes(path);
    ByteString audioBytes = ByteString.copyFrom(data);
    System.out.println("Audio bytes is: " + audioBytes.toString());

    // Configure request with local raw PCM audio.
    RecognitionConfig config =
        RecognitionConfig.newBuilder()
            .setEncoding(AudioEncoding.LINEAR16)
            .setLanguageCode("en-US")
            .setSampleRateHertz(8000)
            .build();
    RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();
    RecognizeResponse response = speech.recognize(config, audio);
    List<SpeechRecognitionResult> results = response.getResultsList();

    for (SpeechRecognitionResult result : results) {
      // Using the first + most likely alternative transcript
      SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
      // Inserts transcript into document.
      String toInsert = alternative.getTranscript();
      insertText(service, toInsert, docId);
    }
  }

  /**
   * Helper function that inserts text into a Google Document.
   * 
   * @param {Object} service Docs authorized service to be able to write to an existing Doc.
   * @param {String} toInsert Text to be inserted into the Doc.
   * @param {String} docID Google Doc ID of the Doc you'll be writing to.
   */
  public static void insertText(Docs service, String toInsert, String docId) throws IOException {
    List<Request> requests = new ArrayList<>();
    requests.add(
        new Request()
            .setInsertText(
                new InsertTextRequest()
                    .setText(toInsert)
                    .setLocation(new Location().setIndex(1))));

    BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest();
    service.documents().batchUpdate(docId, body.setRequests(requests)).execute();
  }
}
