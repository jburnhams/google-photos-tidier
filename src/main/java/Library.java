import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Library {

    private static final Logger logger = LoggerFactory.getLogger(Library.class);
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String FOLDER_QUERY = "mimeType='" + FOLDER_MIME + "' and ";
    private static final String QUERY = FOLDER_QUERY + "title='Google Photos'";
    private static final String APP_NAME = "google-photos-tidier";


    private final java.io.File DATA_STORE_DIR = new java.io.File(".credentials/" + APP_NAME);
    private final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    private final FileDataStoreFactory dataStoreFactory;
    private final HttpTransport httpTransport;
    private final Drive driveService;


    public Library() throws Exception {
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
        driveService = getDriveService();
    }

    private Credential authorize() throws Exception {
        // load client secrets
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory,
                new InputStreamReader(new FileInputStream("./client_secrets.json")));
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets,
                DriveScopes.all()).setDataStoreFactory(dataStoreFactory)
                .build();


        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }


    public Drive getDriveService() throws Exception {
        Credential credential = authorize();
        return new Drive.Builder(httpTransport, jsonFactory, credential).setApplicationName(
                APP_NAME).build();
    }

    public String getPhotoIdFolder() throws IOException {
        FileList photoFolderList = driveService.files().list().setQ(QUERY).execute();
        return photoFolderList.getItems().get(0).getId();
    }

    public void processFolder(String path, String folderId) throws IOException {
        String pageToken = null;
        Map<String, List<File>> titleFiles = new HashMap<>();
        int f = 0;
        do {
            FileList photosList = driveService.files().list()
                    .setPageToken(pageToken)
                    .setQ("'" + folderId + "' in parents").execute();

            for (File file : photosList.getItems()) {
                String title = file.getTitle();
                if (file.getMimeType().equals(FOLDER_MIME)) {
                    logger.info("Found folder: "+ title);
                    processFolder(path+"/"+title, file.getId());
                } else {
                    List<File> files = titleFiles.get(title);
                    if (files == null) {
                        titleFiles.put(title, files = new ArrayList<>());
                    }
                    if (file.getQuotaBytesUsed() > 0) {
                        logger.info(path+"/"+title+" uses "+file.getQuotaBytesUsed());
                    }
                    files.add(file);
                    f++;
                }
            }

            pageToken = photosList.getNextPageToken();
        } while (pageToken != null);
        logger.info("Folder: "+path+", files: "+f+", titles: "+titleFiles.size());
        findDupes(path, titleFiles);
    }

    public List<File> findDupes(String folder, Map<String, List<File>> titleFiles) {
        List<File> toTrash = new ArrayList<>();
        for (String title : titleFiles.keySet()) {
            List<File> dupes = titleFiles.get(title);
            if (dupes.size() == 2) {
                File min = dupes.get(0), max = dupes.get(1);
                if (min.getFileSize() > max.getFileSize()) {
                    min = dupes.get(1);
                    max = dupes.get(0);
                }
                if (max.getQuotaBytesUsed() > 0) {
                    logger.info("Dupes for " + folder + "/" + title + ": " + min.getFileSize() + ", " + max.getFileSize());
                    toTrash.add(max);
                }
            } else if (dupes.size() > 2) {
                logger.info("Found more than 2 files with title "+title+" in "+folder);
            }
        }
        return toTrash;
    }
}
