# Turning Speech to Text with Google APIs & Machine Learning.

_Use the Google Speech-to-Text API to read an audio file's transcript, and use
the Docs API to write the transcript to a new Doc._

Last updated: July, 2019

This solution uses the [Google Speech-to-Text API][speech-api] to read an audio
file and return its written transcript. Then, the [Docs API][docs-api] is used to 
create a new Doc, and write the obtained transcript into this Doc. The code is written
in Java and executed using the gradle build system.

[speech-api]: https://cloud.google.com/speech-to-text/
[docs-api]: https://developers.google.com/docs/api/

## Technology highlights

- Making a call to the Speech-to-Text API to obtain the written transcript
  of an audio file.
- Using the Docs API to create a new Doc.
- Using the Docs API to write to an existing Doc.

## TODO:

- Codelab in the making, stay tuned for its release!
