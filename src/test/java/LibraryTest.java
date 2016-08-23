import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibraryTest {

    private static final Logger logger = LoggerFactory.getLogger(LibraryTest.class);

    @Test
    public void testSomeLibraryMethod() throws Exception {
        Library library = new Library();
        String photoIdFolder = library.getPhotoIdFolder();
        library.processFolder("/", photoIdFolder);
    }
}
