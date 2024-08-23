import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MergeReleaseNotes creates the body of the merged release note for ScalarDB. This script takes
 * release note bodies for ScalarDB, ScalarDB Cluster, ScalarDB GraphQL, and ScalarDB SQL as
 * markdown files as its input. And then output the merged release note body to standard output as a
 * markdown.
 *
 * <p>This script is assumed to be executed in a GitHub Actions workflow.
 *
 * <p>Note that it is needed that java 11 to execute this script since it is executed as a
 * Single-File Source-code program.
 */
@SuppressWarnings("DefaultPackage")
public class MergeReleaseNotes {

  private static final String DEBUG = System.getenv("DEBUG");
  private static final String SECTION_SUMMARY = "Summary";

  private static final Pattern PATTERN_CATEGORY = Pattern.compile("^## *(\\p{Print}+) *$");
  private static final Pattern PATTERN_RELEASE_NOTE_TEXT = Pattern.compile("^ *- *(\\p{Print}+)$");
  private static final Pattern PATTERN_RELEASE_NOTE_TEXT_SPLIT_PRNUMBER =
      Pattern.compile("(.*) +(\\((#[0-9]+ *)+\\))$");

  private final Map<Edition, Map<Category, Map<Repository, List<ReleaseNote>>>> editionMap =
      new EnumMap<>(Edition.class);

  public static void main(String... args) throws Exception {

    if (args.length > 0) {
      if (args[0].equals("-h") || args[0].equals("--help")) {
        System.err.printf("Usage: java %s.java%n", MergeReleaseNotes.class.getSimpleName());
        System.exit(0);
      }
    }

    MergeReleaseNotes mergeReleaseNotes = new MergeReleaseNotes();
    mergeReleaseNotes.createMergedReleaseNote();
  }

  public void createMergedReleaseNote() throws Exception {
    load(new File("scalardb.md"), Edition.COMMUNITY, Repository.DB);
    load(new File("cluster.md"), Edition.ENTERPRISE, Repository.CLUSTER);
    load(new File("graphql.md"), Edition.ENTERPRISE, Repository.GRAPHQL);
    load(new File("sql.md"), Edition.ENTERPRISE, Repository.SQL);
    output();
  }

  void load(File file, Edition edition, Repository repository) throws Exception {
    loadAReleaseNoteBody(edition, repository, file);
  }

  private void loadAReleaseNoteBody(Edition edition, Repository repository, File file)
      throws Exception {
    Category category = null;
    String line;

    try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
      while ((line = reader.readLine()) != null) {
        if (DEBUG != null) System.err.printf("ReadLine: %s%n", line);
        Matcher categoryMatcher = PATTERN_CATEGORY.matcher(line);
        if (categoryMatcher.matches()) {
          if (DEBUG != null) System.err.printf("Matched category: %s%n", categoryMatcher.group(1));
          if (!SECTION_SUMMARY.equalsIgnoreCase(categoryMatcher.group(1)))
            category = Category.getByDisplayName(categoryMatcher.group(1));
          continue;
        } else {
          Matcher releaseNoteTextMatcher = PATTERN_RELEASE_NOTE_TEXT.matcher(line);
          if (releaseNoteTextMatcher.matches()) {
            if (category == null)
              throw new IllegalStateException(
                  "Missing category. Release note text: " + releaseNoteTextMatcher.group(1));
            addReleaseNote(
                new ReleaseNote(edition, category, repository, releaseNoteTextMatcher.group(1)));
          }
        }
      }
    }
  }

  private void addReleaseNote(ReleaseNote releaseNote) {
    Map<Category, Map<Repository, List<ReleaseNote>>> categoryMap =
        editionMap.computeIfAbsent(releaseNote.edition, k -> new EnumMap<>(Category.class));

    Map<Repository, List<ReleaseNote>> repositoryMap =
        categoryMap.computeIfAbsent(releaseNote.category, k -> new EnumMap<>(Repository.class));

    List<ReleaseNote> releaseNotesList =
        repositoryMap.computeIfAbsent(releaseNote.repository, k -> new ArrayList<>());

    if (releaseNote.edition.equals(Edition.ENTERPRISE)) {
      removePullRequestNumbers(releaseNote);
    }

    releaseNotesList.add(releaseNote);
    repositoryMap.put(releaseNote.repository, releaseNotesList);
    categoryMap.put(releaseNote.category, repositoryMap);
    editionMap.put(releaseNote.edition, categoryMap);
  }

  private void removePullRequestNumbers(ReleaseNote releaseNote) {
    Matcher releaseNoteTextMatcher =
        PATTERN_RELEASE_NOTE_TEXT_SPLIT_PRNUMBER.matcher(releaseNote.releaseNoteText);
    if (releaseNoteTextMatcher.matches()) {
      if (DEBUG != null) {
        System.err.printf(
            "Matched::%s::%s grp1:%s grp2:%s%n",
            releaseNote.category,
            releaseNote.repository,
            releaseNoteTextMatcher.group(1),
            releaseNoteTextMatcher.group(2));
      }
      releaseNote.releaseNoteText = releaseNoteTextMatcher.group(1);
    }
  }

  void output() {
    System.out.print("## Summary\n\n");
    Arrays.stream(Edition.values())
        .forEach(
            edition -> {
              outputSections(edition);
              System.out.println();
            });
  }

  private void outputSections(Edition edition) {
    Map<Category, Map<Repository, List<ReleaseNote>>> categoryMap = editionMap.get(edition);
    if (categoryMap == null || categoryMap.isEmpty()) return;

    System.out.printf("## %s edition%n", edition.getEdition());
    Arrays.stream(Category.values())
        .forEach(category -> outputReleaseNotes(category, categoryMap.get(category)));
  }

  private void outputReleaseNotes(
      Category category, Map<Repository, List<ReleaseNote>> repositoryMap) {
    if (repositoryMap == null || repositoryMap.isEmpty()) return;

    System.out.printf("### %s%n", category.getDisplayName());
    Arrays.stream(Repository.values())
        .forEach(
            repository -> {
              List<ReleaseNote> releaseNotes = repositoryMap.get(repository);
              if (releaseNotes != null && !releaseNotes.isEmpty()) {
                /*
                 The merged release note body consists of community edition part and
                 enterprise edition part. The community edition part represents
                 ScalarDB's release note body, meanwhile the enterprise edition
                 represents the rest of repositories release note body. The enterprise
                 edition part shows repository information (ScalarDB Cluster, ScalarDB
                 GraphQL, ScalarDB SQL) under each category section. Thus, the h4 header
                 is needed for the repositories in the enterprise edition.
                */
                if (!repository.equals(Repository.DB))
                  System.out.printf("#### %s%n", repository.getDisplayName());
                for (ReleaseNote rn : releaseNotes) {
                  System.out.printf("- %s%n", rn.releaseNoteText);
                }
              }
            });
  }

  enum Edition {
    COMMUNITY("Community"),
    ENTERPRISE("Enterprise");

    private final String edition;

    Edition(String edition) {
      this.edition = edition;
    }

    public String getEdition() {
      return this.edition;
    }
  }

  enum Category {
    BACKWARD_INCOMPATIBLE("Backward incompatible changes"),
    ENHANCEMENT("Enhancements"),
    IMPROVEMENT("Improvements"),
    BUGFIX("Bug fixes"),
    MISCELLANEOUS("Miscellaneous");

    private final String displayName;

    Category(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return this.displayName;
    }

    public static Category getByDisplayName(String displayName) {
      return Arrays.stream(Category.values())
          .filter(v -> v.getDisplayName().equals(displayName))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Invalid displayName: " + displayName));
    }
  }

  enum Repository {
    DB("ScalarDB"),
    CLUSTER("ScalarDB Cluster"),
    GRAPHQL("ScalarDB GraphQL"),
    SQL("ScalarDB SQL");

    private final String displayName;

    Repository(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return this.displayName;
    }
  }

  static class ReleaseNote {
    Edition edition;
    Category category;
    Repository repository;
    String releaseNoteText;

    public ReleaseNote(
        Edition edition, Category category, Repository repository, String releaseNoteText) {
      this.edition = edition;
      this.category = category;
      this.repository = repository;
      this.releaseNoteText = releaseNoteText;
    }
  }
}
