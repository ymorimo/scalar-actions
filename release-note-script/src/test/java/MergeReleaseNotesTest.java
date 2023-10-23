import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class MergeReleaseNotesTest {

  @Test
  void output_forAllReleaseNotes_outputMergedReleaseNote() throws Exception {
    // Arrange
    MergeReleaseNotes sut = new MergeReleaseNotes();
    loadOneFile(
        sut, MergeReleaseNotes.Edition.COMMUNITY, MergeReleaseNotes.Repository.DB, "scalardb.md");
    loadOneFile(
        sut,
        MergeReleaseNotes.Edition.ENTERPRISE,
        MergeReleaseNotes.Repository.CLUSTER,
        "cluster.md");
    loadOneFile(
        sut,
        MergeReleaseNotes.Edition.ENTERPRISE,
        MergeReleaseNotes.Repository.GRAPHQL,
        "graphql.md");
    loadOneFile(
        sut, MergeReleaseNotes.Edition.ENTERPRISE, MergeReleaseNotes.Repository.SQL, "sql.md");

    final ByteArrayOutputStream baos = new ByteArrayOutputStream(); // Capture the standard output
    System.setOut(new PrintStream(baos, false, StandardCharsets.UTF_8));

    String expected = loadExpectedReleaseNote("expected.md");

    // Act
    sut.output();

    // Assert
    final String stdout = baos.toString(StandardCharsets.UTF_8);
    assertThat(stdout).isEqualTo(expected);
  }

  void loadOneFile(
      MergeReleaseNotes sut,
      MergeReleaseNotes.Edition edition,
      MergeReleaseNotes.Repository repository,
      String fileName)
      throws Exception {
    File file =
        new File(
            Objects.requireNonNull(this.getClass().getClassLoader().getResource(fileName))
                .getFile());
    sut.load(file, edition, repository);
  }

  String loadExpectedReleaseNote(String fileName) throws Exception {
    File file =
        new File(
            Objects.requireNonNull(this.getClass().getClassLoader().getResource(fileName))
                .getFile());
    return Files.readString(file.toPath(), StandardCharsets.UTF_8);
  }
}
