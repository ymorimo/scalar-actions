import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class ReleaseNoteCreationTest {

  // @Test
  @ParameterizedTest
  @MethodSource
  void extractReleaseNoteInfo_normalText_addedCorrectCategory(
      ReleaseNoteCreation.Category category, String rnText) throws Exception {
    // Arrange
    ReleaseNoteCreation sut = new ReleaseNoteCreation(mockedGhContext(category, rnText));

    // Act
    sut.extractReleaseNoteInfo("1");

    // Assert
    Map<ReleaseNoteCreation.Category, List<ReleaseNoteCreation.ReleaseNoteText>> categoryMap =
        sut.categoryMap;
    List<ReleaseNoteCreation.ReleaseNoteText> releaseNoteTexts = categoryMap.get(category);
    assertThat(releaseNoteTexts).isNotNull();
    assertThat(releaseNoteTexts.size()).isEqualTo(1);

    ReleaseNoteCreation.ReleaseNoteText releaseNoteText = releaseNoteTexts.get(0);
    assertThat(releaseNoteText.prNumbers.get(0)).isEqualTo("1");
    assertThat(releaseNoteText.category).isEqualTo(category);
    assertThat(releaseNoteText.text).isEqualTo(rnText);
  }

  @ParameterizedTest
  @EnumSource
  void extractReleaseNoteInfo_notAvailable_shouldBeIgnored(ReleaseNoteCreation.Category category)
      throws Exception {
    // Arrange
    ReleaseNoteCreation sut = new ReleaseNoteCreation(mockedGhContext(category, "N/A"));

    // Act
    sut.extractReleaseNoteInfo("1");

    // Assert
    Map<ReleaseNoteCreation.Category, List<ReleaseNoteCreation.ReleaseNoteText>> categoryMap =
        sut.categoryMap;
    List<ReleaseNoteCreation.ReleaseNoteText> releaseNoteTexts = categoryMap.get(category);
    assertThat(releaseNoteTexts).isNull();
  }

  @ParameterizedTest
  @EnumSource
  void extractReleaseNoteInfo_textIsEmpty_releaseNoteTextIsAStringAsNULL(
      ReleaseNoteCreation.Category category) throws Exception {
    // Arrange
    ReleaseNoteCreation sut = new ReleaseNoteCreation(mockedGhContext(category, ""));

    // Act
    sut.extractReleaseNoteInfo("1");

    // Assert
    Map<ReleaseNoteCreation.Category, List<ReleaseNoteCreation.ReleaseNoteText>> categoryMap =
        sut.categoryMap;
    List<ReleaseNoteCreation.ReleaseNoteText> releaseNoteTexts = categoryMap.get(category);
    assertThat(releaseNoteTexts).isNotNull();
    assertThat(releaseNoteTexts.size()).isEqualTo(1);

    ReleaseNoteCreation.ReleaseNoteText releaseNoteText = releaseNoteTexts.get(0);
    assertThat(releaseNoteText.prNumbers.get(0)).isEqualTo("1");
    assertThat(releaseNoteText.category).isEqualTo(category);
    assertThat(releaseNoteText.text).isEqualTo(null);
  }

  @Test
  void assortSameAsItems_multiplePullRequestsAreTheSameFeature_managedInSameAsItems()
      throws Exception {
    // Arrange
    ReleaseNoteCreation.GitHubContext ghContextMock = mock(ReleaseNoteCreation.GitHubContext.class);
    addMockBehaviourToGitHubContext(
        ghContextMock, "1", ReleaseNoteCreation.Category.ENHANCEMENT, "A topic pull request.");
    addMockBehaviourToGitHubContext(
        ghContextMock, "2", ReleaseNoteCreation.Category.ENHANCEMENT, "Same as #1");
    addMockBehaviourToGitHubContext(
        ghContextMock,
        "3",
        ReleaseNoteCreation.Category.IMPROVEMENT,
        "Additional comment 1.\nSame as #1");
    addMockBehaviourToGitHubContext(
        ghContextMock,
        "4",
        ReleaseNoteCreation.Category.BUGFIX,
        "Same as #1\nAdditional comment 2.");
    addMockBehaviourToGitHubContext(
        ghContextMock, "5", ReleaseNoteCreation.Category.BACKWARD_INCOMPATIBLE, "Same as #1\n");

    ReleaseNoteCreation sut = new ReleaseNoteCreation(ghContextMock);
    sut.extractReleaseNoteInfo("1");
    sut.extractReleaseNoteInfo("2");
    sut.extractReleaseNoteInfo("3");
    sut.extractReleaseNoteInfo("4");
    sut.extractReleaseNoteInfo("5");

    // Act
    sut.assortSameAsItems();

    // Assert
    Map<ReleaseNoteCreation.Category, List<ReleaseNoteCreation.ReleaseNoteText>> categoryMap =
        sut.categoryMap;
    List<ReleaseNoteCreation.ReleaseNoteText> releaseNoteTexts;

    releaseNoteTexts = categoryMap.get(ReleaseNoteCreation.Category.BACKWARD_INCOMPATIBLE);
    assertThat(releaseNoteTexts).isEmpty();
    releaseNoteTexts = categoryMap.get(ReleaseNoteCreation.Category.IMPROVEMENT);
    assertThat(releaseNoteTexts).isEmpty();
    releaseNoteTexts = categoryMap.get(ReleaseNoteCreation.Category.BUGFIX);
    assertThat(releaseNoteTexts).isEmpty();

    releaseNoteTexts = categoryMap.get(ReleaseNoteCreation.Category.ENHANCEMENT);
    assertThat(releaseNoteTexts).isNotNull();
    assertThat(releaseNoteTexts.size()).isEqualTo(1);

    ReleaseNoteCreation.ReleaseNoteText releaseNoteText = releaseNoteTexts.get(0);
    assertThat(releaseNoteText.prNumbers).containsOnly("1", "2", "3", "4", "5");
    assertThat(releaseNoteText.category).isEqualTo(ReleaseNoteCreation.Category.ENHANCEMENT);
    assertThat(releaseNoteText.text)
        .isEqualTo("A topic pull request. Additional comment 1. Additional comment 2.");
  }

  @Test
  void extractReleaseNoteInfo_withoutCategory_addedToMiscellaneousCategory() throws Exception {
    // Arrange
    String rnText = "miscellaneous test";
    ReleaseNoteCreation.GitHubContext ghContextMock = mock(ReleaseNoteCreation.GitHubContext.class);
    when(ghContextMock.isPullRequestMerged(anyString())).thenReturn(true);
    when(ghContextMock.getPullRequestBody(anyString())).thenReturn(normalPullRequestBody(rnText));
    when(ghContextMock.runSubProcessAndGetOutputAsReader(endsWith("--json labels")))
        .thenReturn(null);
    ReleaseNoteCreation sut = new ReleaseNoteCreation(ghContextMock);

    // Act
    sut.extractReleaseNoteInfo("1");

    // Assert
    Map<ReleaseNoteCreation.Category, List<ReleaseNoteCreation.ReleaseNoteText>> categoryMap =
        sut.categoryMap;
    List<ReleaseNoteCreation.ReleaseNoteText> releaseNoteTexts =
        categoryMap.get(ReleaseNoteCreation.Category.MISCELLANEOUS);
    assertThat(releaseNoteTexts).isNotNull();
    assertThat(releaseNoteTexts.size()).isEqualTo(1);

    ReleaseNoteCreation.ReleaseNoteText releaseNoteText = releaseNoteTexts.get(0);
    assertThat(releaseNoteText.prNumbers.get(0)).isEqualTo("1");
    assertThat(releaseNoteText.category).isEqualTo(ReleaseNoteCreation.Category.MISCELLANEOUS);
    assertThat(releaseNoteText.text).isEqualTo(rnText);
  }

  @Test
  void outputReleaseNote_normalReleaseNoteTexts_outputValidReleaseNote() throws Exception {
    // Arrange
    ReleaseNoteCreation.GitHubContext ghContextMock = mock(ReleaseNoteCreation.GitHubContext.class);
    addMockBehaviourToGitHubContext(
        ghContextMock, "1", ReleaseNoteCreation.Category.ENHANCEMENT, "A topic pull request.");
    addMockBehaviourToGitHubContext(
        ghContextMock, "2", ReleaseNoteCreation.Category.ENHANCEMENT, "Same as #1");
    addMockBehaviourToGitHubContext(
        ghContextMock,
        "3",
        ReleaseNoteCreation.Category.IMPROVEMENT,
        "Additional comment 1.\nSame as #1");
    addMockBehaviourToGitHubContext(
        ghContextMock,
        "4",
        ReleaseNoteCreation.Category.BUGFIX,
        "Same as #1\nAdditional comment 2.");
    addMockBehaviourToGitHubContext(
        ghContextMock, "5", ReleaseNoteCreation.Category.IMPROVEMENT, "An improvement text.");
    addMockBehaviourToGitHubContext(
        ghContextMock, "6", ReleaseNoteCreation.Category.BUGFIX, "A bugfix text.");
    addMockBehaviourToGitHubContext(
        ghContextMock,
        "7",
        ReleaseNoteCreation.Category.BACKWARD_INCOMPATIBLE,
        "A backward-incompatible text 1.");
    addMockBehaviourToGitHubContext(
        ghContextMock,
        "8",
        ReleaseNoteCreation.Category.BACKWARD_INCOMPATIBLE,
        "A backward-incompatible text 2.");

    ReleaseNoteCreation sut = new ReleaseNoteCreation(ghContextMock);
    sut.extractReleaseNoteInfo("1");
    sut.extractReleaseNoteInfo("2");
    sut.extractReleaseNoteInfo("3");
    sut.extractReleaseNoteInfo("4");
    sut.extractReleaseNoteInfo("5");
    sut.extractReleaseNoteInfo("6");
    sut.extractReleaseNoteInfo("7");
    sut.extractReleaseNoteInfo("8");
    sut.assortSameAsItems();

    String expected =
        "## Summary\n\n"
            + "## Backward incompatibles\n"
            + "- A backward-incompatible text 1. (#7)\n"
            + "- A backward-incompatible text 2. (#8)\n\n"
            + "## Enhancements\n"
            + "- A topic pull request. Additional comment 1. Additional comment 2. (#1 #2 #3 #4)\n\n"
            + "## Improvements\n"
            + "- An improvement text. (#5)\n\n"
            + "## Bug fixes\n"
            + "- A bugfix text. (#6)\n\n\n";

    final ByteArrayOutputStream baos = new ByteArrayOutputStream(); // Capture the standard output
    System.setOut(new PrintStream(baos, false, StandardCharsets.UTF_8));

    // Act
    sut.outputReleaseNote();

    // Assert
    final String stdout = baos.toString(StandardCharsets.UTF_8);
    assertThat(stdout).isEqualTo(expected);
  }

  static Stream<Arguments> extractReleaseNoteInfo_normalText_addedCorrectCategory() {
    return Stream.of(
        arguments(
            ReleaseNoteCreation.Category.BACKWARD_INCOMPATIBLE,
            "a release note text in backward incompatibles"),
        arguments(ReleaseNoteCreation.Category.ENHANCEMENT, "a release note text in enhancements"),
        arguments(ReleaseNoteCreation.Category.IMPROVEMENT, "a release note text in improvements"),
        arguments(ReleaseNoteCreation.Category.BUGFIX, "a release note text in bug fixes"),
        arguments(
            ReleaseNoteCreation.Category.MISCELLANEOUS, "a release note text in miscellaneous"));
  }

  ReleaseNoteCreation.GitHubContext mockedGhContext(
      ReleaseNoteCreation.Category category, String rnText) throws Exception {
    ReleaseNoteCreation.GitHubContext ghContextMock = mock(ReleaseNoteCreation.GitHubContext.class);
    when(ghContextMock.isPullRequestMerged(anyString())).thenReturn(true);
    when(ghContextMock.getCategoryFromPullRequest(anyString())).thenReturn(category);
    when(ghContextMock.getPullRequestBody(anyString())).thenReturn(normalPullRequestBody(rnText));
    return ghContextMock;
  }

  void addMockBehaviourToGitHubContext(
      ReleaseNoteCreation.GitHubContext mock,
      String prNumber,
      ReleaseNoteCreation.Category category,
      String rnText)
      throws Exception {
    when(mock.isPullRequestMerged(prNumber)).thenReturn(true);
    when(mock.getCategoryFromPullRequest(prNumber)).thenReturn(category);
    when(mock.getPullRequestBody(prNumber)).thenReturn(normalPullRequestBody(rnText));
  }

  BufferedReader normalPullRequestBody(String text) {
    String builder = "## Dummy section\n" + "dummy message\n\n" + "## Release note\n" + text + "\n";
    return new BufferedReader(new StringReader(builder));
  }
}
