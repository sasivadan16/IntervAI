# IntervAI

The complete project is in `IntervAI-GitHub-Upload.zip`.

The ZIP contains:
- `IntervAI_Project.zip`: the supplied Spring Boot and MySQL project.
- `java-backend/`: the current standalone Java backend and the current frontend pages.

## Run the standalone Java app on Windows

Extract the ZIP, open the `java-backend` folder, and run `run-java-backend.bat`. It requires Java 17 or newer. Then visit `http://127.0.0.1:8000/` or `http://127.0.0.1:8000/login.html`.

The standalone backend keeps accounts and sessions in memory; they are cleared when the server stops. It is for local demonstration, not production use.

For the Spring Boot project's MySQL setup, see `IntervAI_Project.zip` → `IntervAI/README.md`.
