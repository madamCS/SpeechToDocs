import com.google.api.client.auth.oauth2.Credential;
import com.google.auth.oauth2.ComputeEngineCredentials;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.docs.v1.model.*;
import com.google.api.services.docs.*;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class Test {
private static String DOCUMENT_ID ="DOCUMENT_ID_GOES_HERE";
private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
/** SPECIFY AUDIO FILE NAME BELOW */
private static final String FILE_NAME = "demo.wav";
private static final String TOKENS_DIRECTORY_PATH = "tokens";
private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
private static final String APPLICATION_NAME = "SpeechToDocs";
private static final List<String> SCOPES = Collections.singletonList(
    DocsScopes.DOCUMENTS);

public static void main(String args[]) throws IOException, GeneralSecurityException {
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Docs service = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

    try {
      createDoc(service);
      playSound(service, "demo.wav");
    } catch (Exception e) {
            e.printStackTrace();
    }
}

    /** Gets Credentials.
     */
    static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        InputStream in = Test.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(
                        new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /** Creates a new Google Document.
     ** Once the document is created, update the DOCUMENT_ID variable with its ID. 
     */
    private static void createDoc(Docs service) throws IOException {
      Document doc = new Document()
                .setTitle("SPEECH TRANSCRIPT");
        doc = service.documents().create(doc)
                .execute();
        System.out.println("Created document with title: " + doc.getId());
    }


  /** Reads the audio file and prints its transcript into the created
   ** Google Document.
   */
  public static void playSound(Docs service, String FILE_NAME) throws IOException {
    SpeechClient speech = SpeechClient.create();
        Path path = Paths.get(FILE_NAME);
        byte[] data = Files.readAllBytes(path);
        ByteString audioBytes = ByteString.copyFrom(data);

        // Configure request with local raw PCM audio.
        RecognitionConfig config =
            RecognitionConfig.newBuilder()
                .setEncoding(AudioEncoding.LINEAR16)
                .setLanguageCode("en-US")
                .setSampleRateHertz(8000)
                .build();
        RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

        // Use blocking call to get audio transcript.
        RecognizeResponse response = speech.recognize(config, audio);
        List<SpeechRecognitionResult> results = response.getResultsList();

        for (SpeechRecognitionResult result : results) {
          // Using the first + most likely alternative transcript
          SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
          // Inserts transcript into document.
          String toInsert = alternative.getTranscript();
          insertText(service, toInsert);
        }
    }

  /** Function that inserts text into a Google Document.
   */
  public static void insertText(Docs service, String WORDS_TO_SAY) throws IOException {
        List<Request> requests = new ArrayList<>();
            requests.add(new Request().setInsertText(new InsertTextRequest()
                    .setText(WORDS_TO_SAY)
                    .setLocation(new Location().setIndex(1))));

            BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest();
            service.documents().batchUpdate(DOCUMENT_ID, body.setRequests(requests)).execute();
    }
} // Closes public class Test