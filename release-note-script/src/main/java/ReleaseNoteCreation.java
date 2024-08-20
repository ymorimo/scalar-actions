import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReleaseNoteCreation creates the body of the release note for a repository of Scalar products. The
 * body of the release note is written out as a markdown to standard output.
 *
 * <p>This script is assumed to be executed in a GitHub Actions workflow.
 *
 * <p>Note that it is needed that java 11 to execute this script since it is executed as a
 * Single-File Source-code program.
 */
@SuppressWarnings("DefaultPackage")
public class ReleaseNoteCreation {

  private static final String DEBUG = System.getenv("DEBUG");
  private static final Pattern PATTERN_RELEASE_NOTE_TEXT = Pattern.compile("^ *-? *(\\p{Print}+)$");
  private static final Pattern PATTERN_SAME_AS_TEXT =
      Pattern.compile("^ *-? *[Ss]ame ?[Aa]s +#?([0-9]+) *$");

  private final GitHubContext ghContext;

  final Map<Category, List<ReleaseNoteText>> categoryMap = new EnumMap<>(Category.class);
  final Map<String, List<ReleaseNoteText>> sameAsItems = new HashMap<>();

  public static void main(String... args) throws Exception {
    if (args.length != 4) {
      System.err.printf(
          "Usage:%n    java %s.java <owner> <projectTitlePrefix> <version>"
              + " <repository>%n%nExample:%n    java %s.java scalar-labs ScalarDB 4.0.0"
              + " scalardb%n",
          ReleaseNoteCreation.class.getSimpleName(), ReleaseNoteCreation.class.getSimpleName());
      System.exit(1);
    }

    /*
    This script is assumed to run on the GitHub Actions workflow and the
    parameters are passed in the workflow automatically. Therefore, the
    validation for the arguments is omitted.
    */
    String owner = args[0];
    String projectTitlePrefix = args[1];
    String version = args[2];
    String repository = args[3];

    ReleaseNoteCreation main =
        new ReleaseNoteCreation(owner, projectTitlePrefix, version, repository);
    main.createReleaseNote();
  }

  public ReleaseNoteCreation(
      String owner, String projectTitlePrefix, String version, String repository) {
    ghContext = new GitHubContext(owner, projectTitlePrefix, version, repository);
  }

  /** This constructor is only for test */
  public ReleaseNoteCreation(GitHubContext ghContext) {
    this.ghContext = ghContext;
  }

  public void createReleaseNote() throws Exception {
    String projectId = ghContext.getProjectId();
    List<String> prNumbers = ghContext.getPullRequestNumbers(projectId);

    for (String prNumber : prNumbers) {
      extractReleaseNoteInfo(prNumber);
    }
    assortSameAsItems();
    outputReleaseNote();
  }

  void extractReleaseNoteInfo(String prNumber) throws Exception {
    if (!ghContext.isPullRequestMerged(prNumber)) return;

    Category category = ghContext.getCategoryFromPullRequest(prNumber);
    BufferedReader br = ghContext.getPullRequestBody(prNumber);

    String line;
    while ((line = br.readLine()) != null) {
      if (Pattern.matches("^## *[Rr]elease *[Nn]otes? *", line)) {
        break;
      }
    }

    ReleaseNoteText releaseNoteText = extractReleaseNoteText(category, prNumber, br);
    if (releaseNoteText != null) {
      categorizeReleaseNoteText(releaseNoteText);
    }
  }

  private ReleaseNoteText extractReleaseNoteText(
      Category category, String prNumber, BufferedReader br) throws Exception {
    ReleaseNoteText releaseNoteText = new ReleaseNoteText();
    releaseNoteText.category = category;
    releaseNoteText.prNumbers.add(prNumber);

    String line;
    while ((line = br.readLine()) != null) {
      if (Pattern.matches("^## *.*", line))
        break; // Reached to the next section header (ended release note section)
      if (Pattern.matches("^ *-? *N/?A *$", line)) return null; // This PR is not user-facing

      Matcher releseNoteTextMatcher =
          PATTERN_RELEASE_NOTE_TEXT.matcher(line); // Extract Release note text
      if (releseNoteTextMatcher.matches()) {
        if (!Pattern.matches("^ *-? *[Ss]ame ?[Aa]s +#?([0-9]+) *$", line)) {
          String matched = releseNoteTextMatcher.group(1);
          if (DEBUG != null) System.err.printf("matched: %s%n", matched);
          releaseNoteText.text = releseNoteTextMatcher.group(1);
        }
      }

      Matcher sameAsTextMatcher = PATTERN_SAME_AS_TEXT.matcher(line); // It has a related PR
      if (sameAsTextMatcher.matches()) {
        String topicPrNumber = sameAsTextMatcher.group(1);
        if (DEBUG != null)
          System.err.printf("PR:%s sameAs:%s%n", releaseNoteText.prNumbers.get(0), topicPrNumber);
        List<ReleaseNoteText> relatedPrs =
            sameAsItems.computeIfAbsent(topicPrNumber, k -> new ArrayList<>());
        relatedPrs.add(releaseNoteText);
        sameAsItems.put(topicPrNumber, relatedPrs);
      }
    }

    return releaseNoteText;
  }

  private void categorizeReleaseNoteText(ReleaseNoteText rnText) {
    setMiscellaneousCategoryIfCategoryIsNull(rnText);
    Arrays.stream(Category.values())
        .forEach(
            category -> {
              if (rnText.category.equals(category)) {
                List<ReleaseNoteText> releaseNoteTexts =
                    categoryMap.computeIfAbsent(category, k -> new ArrayList<>());
                if (!isContainedInSameAsItems(rnText)) releaseNoteTexts.add(rnText);
                categoryMap.put(category, releaseNoteTexts);
              }
            });
  }

  void assortSameAsItems() {
    for (Entry<String, List<ReleaseNoteText>> entry : sameAsItems.entrySet()) {
      String topicPrNumber = entry.getKey();
      List<ReleaseNoteText> releaseNoteTextsInSameAs = entry.getValue();

      Arrays.stream(Category.values())
          .forEach(
              category -> {
                List<ReleaseNoteText> releaseNoteTextsInACategory = categoryMap.get(category);
                if (releaseNoteTextsInACategory != null) {
                  releaseNoteTextsInACategory.forEach(
                      rnInfo -> {
                        if (rnInfo.prNumbers.get(0).equals(topicPrNumber)) {
                          releaseNoteTextsInSameAs.forEach(from -> merge(from, rnInfo));
                        }
                      });
                }
              });
    }
  }

  private void merge(ReleaseNoteText from, ReleaseNoteText to) {
    if (from.text != null && !from.text.isEmpty()) {
      to.text = to.text + " " + from.text;
    }
    if (DEBUG != null) System.err.printf("merged RN text: %s%n", to.text);
    to.prNumbers.addAll(from.prNumbers);
  }

  void outputReleaseNote() {
    StringBuilder builder = new StringBuilder();
    builder.append("## Summary\n\n");

    Arrays.stream(Category.values())
        .forEach(
            category -> {
              List<ReleaseNoteText> releaseNotes = categoryMap.get(category);
              if (releaseNotes != null && !releaseNotes.isEmpty()) {
                builder.append(String.format("## %s%n", category.getDisplayName()));
                builder.append(getFormattedReleaseNotes(releaseNotes)).append("\n");
              }
            });
    System.out.println(builder);
  }

  private String getFormattedReleaseNotes(List<ReleaseNoteText> releaseNotes) {
    StringBuilder builder = new StringBuilder();
    releaseNotes.forEach(
        rnText -> {
          builder.append(String.format("- %s (", rnText.text));
          rnText.prNumbers.forEach(prNum -> builder.append(String.format("#%s ", prNum)));
          builder.deleteCharAt(builder.length() - 1); // delete the last space character
          builder.append(")\n");
        });
    return builder.toString();
  }

  private void setMiscellaneousCategoryIfCategoryIsNull(ReleaseNoteText rnText) {
    if (rnText.category == null) {
      rnText.category = Category.MISCELLANEOUS;
    }
  }

  private boolean isContainedInSameAsItems(ReleaseNoteText rnText) {
    return sameAsItems.values().stream().anyMatch(items -> items.contains(rnText));
  }

  public enum Category {
    BACKWARD_INCOMPATIBLE("Backward incompatible changes", "backward-incompatible"),
    ENHANCEMENT("Enhancements", "enhancement"),
    IMPROVEMENT("Improvements", "improvement"),
    BUGFIX("Bug fixes", "bugfix"),
    MISCELLANEOUS("Miscellaneous", "miscellaneous");

    private final String displayName;
    private final String label;

    Category(String displayName, String label) {
      this.displayName = displayName;
      this.label = label;
    }

    public String getDisplayName() {
      return this.displayName;
    }

    public String getLabel() {
      return this.label;
    }

    public static Category fromLabel(String label) {
      return Arrays.stream(Category.values())
          .filter(v -> v.getLabel().equalsIgnoreCase(label))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Invalid label: " + label));
    }
  }

  static class ReleaseNoteText {
    public Category category;
    public String text;
    public List<String> prNumbers = new ArrayList<>();
  }

  public static class GitHubContext {

    private static final String MERGED_STATE = "merged";
    private static final int LIMIT_NUMBER_OF_RETRIEVE_PULL_REQUESTS = 10000;

    private final String owner;
    private final String projectTitlePrefix;
    private final String version;
    private final String repository;

    public GitHubContext(String owner, String projectTitString, String version, String repository) {
      this.owner = owner;
      this.projectTitlePrefix = projectTitString;
      this.version = version;
      this.repository = repository;
    }

    private String getProjectId() throws Exception {
      /*
       * Includes closed project if we get the project list so that we can run
       * this script to the closed project for debug.
       */
      BufferedReader br =
          runSubProcessAndGetOutputAsReader(
              format(
                  "gh project list --owner %s --closed | awk '/%s/ {print}' | awk '/%s/ {print $1}'",
                  this.owner, this.projectTitlePrefix, getVersion()));

      String line = br.readLine(); // Assuming only one line exists.
      if (line == null) throw new RuntimeException("Couldn't get the projectId");
      return line;
    }

    private String getVersion() {
      int index = this.version.indexOf("-");
      if (index == -1) {
        return this.version;
      }
      // Remove the suffix after the dash. (e.g., 4.0.0-rc1 -> 4.0.0)
      return this.version.substring(0, index);
    }

    private List<String> getPullRequestNumbers(String projectId) throws Exception {
      BufferedReader br =
          runSubProcessAndGetOutputAsReader(
              format(
                  "gh project item-list %s --owner %s --limit %d | awk -F'\\t' '/\\/%s\\t/ {print"
                      + " $3}'",
                  projectId, this.owner, LIMIT_NUMBER_OF_RETRIEVE_PULL_REQUESTS, this.repository));

      String line;
      List<String> prNumbers = new ArrayList<>();
      while ((line = br.readLine()) != null) {
        prNumbers.add(line);
      }
      return prNumbers;
    }

    private String getPullRequestState(String prNumber) throws Exception {
      BufferedReader br =
          runSubProcessAndGetOutputAsReader(
              format(
                  "gh pr view %s --repo %s/%s --jq \".state\" --json state",
                  prNumber, this.owner, this.repository));

      String line = br.readLine(); // Assuming only one line exists.
      if (line == null) throw new RuntimeException("Couldn't get the project state");
      return line;
    }

    boolean isPullRequestMerged(String prNumber) throws Exception {
      String state = getPullRequestState(prNumber);
      return MERGED_STATE.equalsIgnoreCase(state);
    }

    Category getCategoryFromPullRequest(String prNumber) throws Exception {
      BufferedReader br =
          runSubProcessAndGetOutputAsReader(
              format(
                  "gh pr view %s --repo %s/%s --jq \".labels[].name\" --json labels",
                  prNumber, this.owner, this.repository));

      String line;
      while ((line = br.readLine()) != null) {
        if (isValidCategory(line)) return Category.fromLabel(line);
      }
      return Category.MISCELLANEOUS;
    }

    BufferedReader getPullRequestBody(String prNumber) throws Exception {
      return runSubProcessAndGetOutputAsReader(
          format(
              "gh pr view %s --repo %s/%s --jq \".body\" --json body",
              prNumber, this.owner, this.repository));
    }

    BufferedReader runSubProcessAndGetOutputAsReader(String command) throws Exception {
      if (DEBUG != null) System.err.printf("Executed: %s%n", command);
      Process p = new ProcessBuilder("bash", "-c", command).start();
      p.waitFor();
      return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
    }

    private boolean isValidCategory(String category) {
      return Arrays.stream(Category.values())
          .anyMatch(target -> target.getLabel().equalsIgnoreCase(category));
    }
  }
}
