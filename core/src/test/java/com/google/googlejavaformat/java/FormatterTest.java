/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.googlejavaformat.java;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.io.CharStreams;
import com.google.googlejavaformat.java.JavaFormatterOptions.Style;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration test for google-java-format. */
@RunWith(JUnit4.class)
public final class FormatterTest {

  @Rule public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testFormatAosp() throws Exception {
    // don't forget to misspell "long", or you will be mystified for a while
    String input =
        "class A{void b(){while(true){weCanBeCertainThatThisWillEndUpGettingWrapped("
            + "because, it, is, just, so, very, very, very, very, looong);}}}";
    String expectedOutput =
        """
        class A {
            void b() {
                while (true) {
                    weCanBeCertainThatThisWillEndUpGettingWrapped(
                            because, it, is, just, so, very, very, very, very, looong);
                }
            }
        }
        """;

    Path tmpdir = testFolder.newFolder().toPath();
    Path path = tmpdir.resolve("A.java");
    Files.writeString(path, input);

    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();

    Main main = new Main(new PrintWriter(out, true), new PrintWriter(err, true), System.in);
    String[] args = {"--aosp", path.toString()};
    assertThat(main.format(args)).isEqualTo(0);
    assertThat(out.toString()).isEqualTo(expectedOutput);
  }

  @Test
  public void testFormatNonJavaFiles() throws Exception {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    Main main = new Main(new PrintWriter(out, true), new PrintWriter(err, true), System.in);

    // should succeed because non-Java files are skipped
    assertThat(main.format("foo.go")).isEqualTo(0);
    assertThat(err.toString()).contains("Skipping non-Java file: foo.go");

    // format still fails on missing files
    assertThat(main.format("Foo.java")).isEqualTo(1);
    assertThat(err.toString()).contains("Foo.java: could not read file: ");
  }

  @Test
  public void testFormatStdinStdoutWithDashFlag() throws Exception {
    String input =
        """
        class Foo{
        void f
        () {
        }
        }
        """;
    String expectedOutput =
        """
        class Foo {
          void f() {}
        }
        """;

    InputStream in = new ByteArrayInputStream(input.getBytes(UTF_8));
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();

    InputStream oldIn = System.in;
    System.setIn(in);

    Main main = new Main(new PrintWriter(out, true), new PrintWriter(err, true), System.in);
    assertThat(main.format("-")).isEqualTo(0);
    assertThat(out.toString()).isEqualTo(expectedOutput);

    System.setIn(oldIn);
  }

  @Test
  public void testFormatLengthUpToEOF() throws Exception {
    String input =
        """
        class Foo{
        void f
        () {
        }
        }\n\n\n\n\n
        """;
    String expectedOutput =
        """
        class Foo {
          void f() {}
        }
        """;

    Path tmpdir = testFolder.newFolder().toPath();
    Path path = tmpdir.resolve("Foo.java");
    Files.writeString(path, input);

    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();

    Main main = new Main(new PrintWriter(out, true), new PrintWriter(err, true), System.in);
    String[] args = {"--offset", "0", "--length", String.valueOf(input.length()), path.toString()};
    assertThat(main.format(args)).isEqualTo(0);
    assertThat(out.toString()).isEqualTo(expectedOutput);
  }

  @Test
  public void testFormatLengthOutOfRange() throws Exception {
    String input = "class Foo{}\n";

    Path tmpdir = testFolder.newFolder().toPath();
    Path path = tmpdir.resolve("Foo.java");
    Files.writeString(path, input);

    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();

    Main main = new Main(new PrintWriter(out, true), new PrintWriter(err, true), System.in);
    String[] args = {"--offset", "0", "--length", "9999", path.toString()};
    assertThat(main.format(args)).isEqualTo(1);
    assertThat(err.toString())
        .contains("error: invalid offset (0) or length (9999); offset + length (9999)");
  }

  @Test
  public void testFormatOffsetOutOfRange() throws Exception {
    String input = "class Foo{}\n";

    Path tmpdir = testFolder.newFolder().toPath();
    Path path = tmpdir.resolve("Foo.java");
    Files.writeString(path, input);

    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();

    Main main = new Main(new PrintWriter(out, true), new PrintWriter(err, true), System.in);
    String[] args = {"--offset", "9998", "--length", "1", path.toString()};
    assertThat(main.format(args)).isEqualTo(1);
    assertThat(err.toString())
        .contains("error: invalid offset (9998) or length (1); offset + length (9999)");
  }

  @Test
  public void blankInClassBody() throws FormatterException {
    String input =
        """
        package test;
        class T {

        }
        """;
    String output = new Formatter().formatSource(input);
    String expect =
        """
        package test;

        class T {}
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void blankInClassBodyNoTrailing() throws FormatterException {
    String input =
        """
        package test;
        class T {

        }\
        """;
    String output = new Formatter().formatSource(input);
    String expect =
        """
        package test;

        class T {}
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void docCommentTrailingBlank() throws FormatterException {
    String input =
        """
        class T {
        /** asd */

        int x;
        }\
        """;
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class T {
          /** asd */
          int x;
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void blockCommentInteriorTrailingBlank() throws FormatterException {
    String input =
        """
        class T {
        /*
        * asd
        * fgh
        */

        int x;
        }\
        """;
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class T {
          /*
           * asd
           * fgh
           */

          int x;
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void blockCommentTrailingBlank() throws FormatterException {
    String input =
        """
        class T {
        /* asd */

        int x;
        }\
        """;
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class T {
          /* asd */

          int x;
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void lineCommentTrailingBlank() throws FormatterException {
    String input =
        """
        class T {
        // asd

        int x;
        }\
        """;
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class T {
          // asd

          int x;
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void lineCommentTrailingThinSpace() throws FormatterException {
    // The Unicode thin space is matched by CharMatcher.whitespace() but not trim().
    String input = "class T {\n  // asd\u2009\n}\n";
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class T {
          // asd
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void noBlankAfterLineCommentWithInteriorBlankLine() throws FormatterException {
    String input =
        """
        class T {
        // asd

        // dsa
        int x;
        }\
        """;
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class T {
          // asd

          // dsa
          int x;
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void badConstructor() throws FormatterException {
    String input = "class X { Y() {} }";
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class X {
          Y() {}
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void voidMethod() throws FormatterException {
    String input = "class X { void Y() {} }";
    String output = new Formatter().formatSource(input);
    String expect =
        """
        class X {
          void Y() {}
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  private static final String UNORDERED_IMPORTS =
      """
      import com.google.common.base.Preconditions;

      import static org.junit.Assert.fail;
      import static com.google.truth.Truth.assertThat;

      import org.junit.runners.JUnit4;
      import org.junit.runner.RunWith;

      import java.util.List;

      import javax.annotation.Nullable;
      """;

  @Test
  public void importsNotReorderedByDefault() throws FormatterException {
    String input =
        "package com.google.example;\n" + UNORDERED_IMPORTS + "public class ExampleTest {}\n";
    String output = new Formatter().formatSource(input);
    String expect =
        "package com.google.example;\n\n" + UNORDERED_IMPORTS + "\npublic class ExampleTest {}\n";
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void importsFixedIfRequested() throws FormatterException {
    String input =
        "package com.google.example;\n"
            + UNORDERED_IMPORTS
            + """
            public class ExampleTest {
              @Nullable List<?> xs;
            }
            """;
    String output = new Formatter().formatSourceAndFixImports(input);
    String expect =
        """
        package com.google.example;

        import java.util.List;
        import javax.annotation.Nullable;

        public class ExampleTest {
          @Nullable List<?> xs;
        }
        """;
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void importOrderingWithoutFormatting() throws IOException, UsageException {
    importOrdering(
        "--fix-imports-only", "com/google/googlejavaformat/java/testimports/A.imports-only");
  }

  @Test
  public void importOrderingAndFormatting() throws IOException, UsageException {
    importOrdering(null, "com/google/googlejavaformat/java/testimports/A.imports-and-formatting");
  }

  @Test
  public void formattingWithoutImportOrdering() throws IOException, UsageException {
    importOrdering(
        "--skip-sorting-imports",
        "com/google/googlejavaformat/java/testimports/A.formatting-and-unused-import-removal");
  }

  @Test
  public void formattingWithoutRemovingUnusedImports() throws IOException, UsageException {
    importOrdering(
        "--skip-removing-unused-imports",
        "com/google/googlejavaformat/java/testimports/A.formatting-and-import-sorting");
  }

  private void importOrdering(String sortArg, String outputResourceName)
      throws IOException, UsageException {
    Path tmpdir = testFolder.newFolder().toPath();
    Path path = tmpdir.resolve("Foo.java");

    String inputResourceName = "com/google/googlejavaformat/java/testimports/A.input";
    String input = getResource(inputResourceName);
    String expectedOutput = getResource(outputResourceName);
    Files.writeString(path, input);

    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    Main main = new Main(new PrintWriter(out, true), new PrintWriter(err, true), System.in);
    String[] args =
        sortArg != null
            ? new String[] {sortArg, "-i", path.toString()}
            : new String[] {"-i", path.toString()};
    main.format(args);

    assertThat(err.toString()).isEmpty();
    assertThat(out.toString()).isEmpty();
    String output = new String(Files.readAllBytes(path), UTF_8);
    assertThat(output).isEqualTo(expectedOutput);
  }

  private String getResource(String resourceName) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
      assertWithMessage("Missing resource: %s", resourceName).that(stream).isNotNull();
      return CharStreams.toString(new InputStreamReader(stream, UTF_8));
    }
  }

  // regression test for google-java-format#47
  @Test
  public void testTrailingCommentWithoutTerminalNewline() throws Exception {
    assertThat(new Formatter().formatSource("/*\n * my comment */"))
        .isEqualTo("/*\n * my comment */\n");
  }

  @Test
  public void testEmptyArray() throws Exception {
    assertThat(new Formatter().formatSource("class T { int x[] = {,}; }"))
        .isEqualTo(
            """
            class T {
              int x[] = {,};
            }
            """);
  }

  @Test
  public void stringEscapeLength() throws Exception {
    assertThat(new Formatter().formatSource("class T {{ f(\"\\\"\"); }}"))
        .isEqualTo(
            """
            class T {
              {
                f(\"\\\"\");
              }
            }
            """);
  }

  @Test
  public void wrapLineComment() throws Exception {
    assertThat(
            new Formatter()
                .formatSource(
"""
class T {
  public static void main(String[] args) { // one long incredibly unbroken sentence moving from topic to topic so that no-one had a chance to interrupt;
  }
}
"""))
        .isEqualTo(
"""
class T {
  public static void main(
      String[]
          args) { // one long incredibly unbroken sentence moving from topic to topic so that no-one
                  // had a chance to interrupt;
  }
}
""");
  }

  @Test
  public void onlyWrapLineCommentOnWhitespace() throws Exception {
    assertThat(
            new Formatter()
                .formatSource(
"""
class T {
  public static void main(String[] args) { // one_long_incredibly_unbroken_sentence_moving_from_topic_to_topic_so_that_no-one_had_a_chance_to_interrupt;
  }
}
"""))
        .isEqualTo(
"""
class T {
  public static void main(
      String[]
          args) { // one_long_incredibly_unbroken_sentence_moving_from_topic_to_topic_so_that_no-one_had_a_chance_to_interrupt;
  }
}
""");
  }

  @Test
  public void onlyWrapLineCommentOnWhitespace_noLeadingWhitespace() throws Exception {
    assertThat(
            new Formatter()
                .formatSource(
"""
class T {
  public static void main(String[] args) { //one_long_incredibly_unbroken_sentence_moving_from_topic_to_topic_so_that_no-one_had_a_chance_to_interrupt;
  }
}
"""))
        .isEqualTo(
"""
class T {
  public static void main(
      String[]
          args) { // one_long_incredibly_unbroken_sentence_moving_from_topic_to_topic_so_that_no-one_had_a_chance_to_interrupt;
  }
}
""");
  }

  @Test
  public void throwsFormatterException() throws Exception {
    assertThrows(
        FormatterException.class,
        () -> new Formatter().formatSourceAndFixImports("package foo; public class {"));
  }

  @Test
  public void blankLinesImportComment() throws FormatterException {
    String withBlank =
        """
        package p;

        /** test */

        import a.A;

        class T {
          A a;
        }
        """;
    String withoutBlank =
        """
        package p;

        /** test */
        import a.A;

        class T {
          A a;
        }
        """;

    // Formatting deletes the blank line between the "javadoc" and the first import.
    assertThat(new Formatter().formatSource(withBlank)).isEqualTo(withoutBlank);
    assertThat(new Formatter().formatSourceAndFixImports(withBlank)).isEqualTo(withoutBlank);
    assertThat(new Formatter().formatSource(withoutBlank)).isEqualTo(withoutBlank);
    assertThat(new Formatter().formatSourceAndFixImports(withoutBlank)).isEqualTo(withoutBlank);

    // Just fixing imports preserves whitespace around imports.
    assertThat(RemoveUnusedImports.removeUnusedImports(withBlank)).isEqualTo(withBlank);
    assertThat(ImportOrderer.reorderImports(withBlank, Style.GOOGLE)).isEqualTo(withBlank);
    assertThat(RemoveUnusedImports.removeUnusedImports(withoutBlank)).isEqualTo(withoutBlank);
    assertThat(ImportOrderer.reorderImports(withoutBlank, Style.GOOGLE)).isEqualTo(withoutBlank);
  }

  @Test
  public void dontWrapMoeLineComments() throws Exception {
    assertThat(
            new Formatter()
                .formatSource(
"""
class T {
  // MOE: one long incredibly unbroken sentence moving from topic to topic so that no-one had a chance to interrupt;
}
"""))
        .isEqualTo(
"""
class T {
  // MOE: one long incredibly unbroken sentence moving from topic to topic so that no-one had a chance to interrupt;
}
""");
  }

  @Test
  public void testLocalStyleChanges() throws Exception {
    // 1. @Override on the same line
    String inputOverride =
        """
        class T {
          @Override
          public String toString() {
            return "";
          }
        }
        """;
    String expectedOverride =
        """
        class T {
          @Override public String toString() {
            return "";
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputOverride)).isEqualTo(expectedOverride);

    // 2. Short arguments wrapping in prose-style (independent)
    String inputArguments =
        """
        class T {
          void f() {
            foo(firstLongArgumentName, secondLongArgumentName, thirdLongArgumentName, fourthLongArgumentName, fifthLongArgumentName);
          }
        }
        """;
    String expectedArguments =
        """
        class T {
          void f() {
            foo(
                firstLongArgumentName, secondLongArgumentName, thirdLongArgumentName,
                fourthLongArgumentName, fifthLongArgumentName);
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputArguments)).isEqualTo(expectedArguments);

    // 3. Rectangle rule for method invocation chains (no break after `=`)
    String inputChain =
        """
        class T {
          void f() {
            Foo foo = Foo.newBuilder().setFirstLongPropertyName(firstLongArgumentName).setSecondLongPropertyName(secondLongArgumentName).setThirdLongPropertyName(thirdLongArgumentName).build();
          }
        }
        """;
    String expectedChain =
        """
        class T {
          void f() {
            Foo foo = Foo.newBuilder()
                .setFirstLongPropertyName(firstLongArgumentName)
                .setSecondLongPropertyName(secondLongArgumentName)
                .setThirdLongPropertyName(thirdLongArgumentName)
                .build();
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputChain)).isEqualTo(expectedChain);

    // 4. Rectangle rule for lambdas (no break after `->`)
    String inputLambda =
        """
        class T {
          void f() {
            Runnable r = () -> Foo.newBuilder().setFirstLongPropertyName(firstLongArgumentName).setSecondLongPropertyName(secondLongArgumentName).setThirdLongPropertyName(thirdLongArgumentName).build();
          }
        }
        """;
    String expectedLambda =
        """
        class T {
          void f() {
            Runnable r = () -> Foo.newBuilder()
                .setFirstLongPropertyName(firstLongArgumentName)
                .setSecondLongPropertyName(secondLongArgumentName)
                .setThirdLongPropertyName(thirdLongArgumentName)
                .build();
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputLambda)).isEqualTo(expectedLambda);

    // 5. Rectangle rule for switch rules (no break after `->`)
    String inputSwitch =
        """
        class T {
          void f(int x) {
            switch (x) {
              case 1 -> Foo.newBuilder().setFirstLongPropertyName(firstLongArgumentName).setSecondLongPropertyName(secondLongArgumentName).setThirdLongPropertyName(thirdLongArgumentName).build();
            }
          }
        }
        """;
    String expectedSwitch =
        """
        class T {
          void f(int x) {
            switch (x) {
              case 1 -> Foo.newBuilder()
                  .setFirstLongPropertyName(firstLongArgumentName)
                  .setSecondLongPropertyName(secondLongArgumentName)
                  .setThirdLongPropertyName(thirdLongArgumentName)
                  .build();
            }
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputSwitch)).isEqualTo(expectedSwitch);

    // 6. Rectangle rule for nested method call arguments inside switch rules (must break after `(`
    // if method call not on its own line)
    String inputNestedSwitchCall =
        """
        class T {
          void f(int x) {
            switch (x) {
              case 1 -> builder.setCallExpr(Expr.Call.newBuilder().setFunction("_[_]").addArgs(convert(v.operand())).addArgs(convert(v.index())));
            }
          }
        }
        """;
    String expectedNestedSwitchCall =
        """
        class T {
          void f(int x) {
            switch (x) {
              case 1 -> builder.setCallExpr(
                  Expr.Call.newBuilder()
                      .setFunction("_[_]")
                      .addArgs(convert(v.operand()))
                      .addArgs(convert(v.index())));
            }
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputNestedSwitchCall))
        .isEqualTo(expectedNestedSwitchCall);

    // Test to check why toMacro arguments break
    String inputToMacro =
        """
        class T {
          void f() {
            toMacro(target, method.name(), args, (t, v, c1, c2) -> new CelExpr.Macro.FilterMap(t, v, c1, c2));
          }
        }
        """;
    String expectedToMacro =
        """
        class T {
          void f() {
            toMacro(
                target, method.name(), args, (t, v, c1, c2) -> new CelExpr.Macro.FilterMap(t, v, c1, c2));
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputToMacro)).isEqualTo(expectedToMacro);

    // 7. Head section does not fit on same line but fits on next line
    String inputForceBreak =
        """
        class T {
          void f() {
            Foo foo = SomeVeryLongClassNameToNotFitOnSameLine3456789012345678901234567890123456789.newBuilder().setProp(val).build();
          }
        }
        """;
    String expectedForceBreak =
        """
        class T {
          void f() {
            Foo foo =
                SomeVeryLongClassNameToNotFitOnSameLine3456789012345678901234567890123456789.newBuilder()
                    .setProp(val)
                    .build();
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputForceBreak)).isEqualTo(expectedForceBreak);

    // 7. Nested switch expression inside switch rule
    String inputNestedSwitch =
        """
        class T {
          int f(int x, String s) {
            return switch (x) {
              case 1 -> switch (s) {
                case "x" -> 1;
                default -> 2;
              };
              default -> 0;
            };
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputNestedSwitch)).isEqualTo(inputNestedSwitch);

    // 8. Lambda: Head section does not fit on same line as `->` but fits on next line
    String inputLambdaForceBreak =
        """
        class T {
          void f() {
            Runnable r = () -> SomeVeryLongClassNameToNotFitOnSameLine3456789012345678901234567890123456789.newBuilder().setProp(val).build();
          }
        }
        """;
    String expectedLambdaForceBreak =
        """
        class T {
          void f() {
            Runnable r = () ->
                SomeVeryLongClassNameToNotFitOnSameLine3456789012345678901234567890123456789.newBuilder()
                    .setProp(val)
                    .build();
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputLambdaForceBreak))
        .isEqualTo(expectedLambdaForceBreak);

    // 9. Switch case: Head section does not fit on same line as `->` but fits on next line
    String inputSwitchForceBreak =
        """
        class T {
          void f(int x) {
            switch (x) {
              case 1 -> SomeVeryLongClassNameToNotFitOnSameLine345678901234567890123456789012345.newBuilder().setProp(val).build();
            }
          }
        }
        """;
    String expectedSwitchForceBreak =
        """
        class T {
          void f(int x) {
            switch (x) {
              case 1 ->
                  SomeVeryLongClassNameToNotFitOnSameLine345678901234567890123456789012345.newBuilder()
                      .setProp(val)
                      .build();
            }
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputSwitchForceBreak))
        .isEqualTo(expectedSwitchForceBreak);

    // 10. Method parameter list prose-style wrapping
    String inputParams =
        """
        class T {
          void f(int parameterOne, int parameterTwo, int parameterThree, int parameterFour, int parameterFive, int parameterSix) {}
        }
        """;

    String expectedParams =
        """
        class T {
          void f(
              int parameterOne, int parameterTwo, int parameterThree, int parameterFour, int parameterFive,
              int parameterSix) {}
        }
        """;
    assertThat(new Formatter().formatSource(inputParams)).isEqualTo(expectedParams);

    // 11. Lambda parameter list prose-style wrapping
    String inputLambdaParams =
        """
        class T {
          void f() {
            Runnable r = (int parameterOne, int parameterTwo, int parameterThree, int parameterFour, int parameterFive, int parameterSix) -> {};
          }
        }
        """;
    String expectedLambdaParams =
        """
        class T {
          void f() {
            Runnable r = (int parameterOne, int parameterTwo, int parameterThree, int parameterFour,
                int parameterFive, int parameterSix) -> {};
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputLambdaParams)).isEqualTo(expectedLambdaParams);

    // 12. Method parameters: fit flat on next line but not same line
    String inputParamsNextLine =
        """
        interface T {
          void f(int parameterOne, int parameterTwo, int parameterThree, int parameterFour, int parameterFive);
        }
        """;
    String expectedParamsNextLine =
        """
        interface T {
          void f(
              int parameterOne, int parameterTwo, int parameterThree, int parameterFour, int parameterFive);
        }
        """;
    assertThat(new Formatter().formatSource(inputParamsNextLine)).isEqualTo(expectedParamsNextLine);

    // 13. Guard statement same line formatting
    String inputGuard =
        """
        class T {
          int f(boolean x) {
            if (x) return;
            if (x) return 1;
            if (x) return a;
            if (x) return foo(a, b);
            if (x) return a + b;
            if (x) return foo(bar());
            if (x) return a.b.c.foo();
            if (x) return veryLongMethodNameWithManyArgumentsToForceItToWrapNoMatterWhatAndBeSuperLong(first, second, third);
          }
        }
        """;
    String expectedGuard =
        """
        class T {
          int f(boolean x) {
            if (x) return;
            if (x) return 1;
            if (x) return a;
            if (x) return foo(a, b);
            if (x) return a + b;
            if (x) return foo(bar());
            if (x) return a.b.c.foo();
            if (x)
              return veryLongMethodNameWithManyArgumentsToForceItToWrapNoMatterWhatAndBeSuperLong(
                  first, second, third);
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputGuard)).isEqualTo(expectedGuard);

    // 14. Format-method calls formatting
    String inputFormat =
        """
        class T {
          void f() {
            String s = String.format("simple template", firstArgumentName, secondArgumentName, thirdArgumentNameLong);
            String s2 = String.format("this is a very long template string to force wrapping of arguments after it", firstArgumentName, secondArgumentName, thirdArgumentName);
            String s3 = String.format("short fit");
            Preconditions.checkState(expressionStateConditionToCheck, "this is a very long checkState template to force wrapping", firstArgumentName, secondArgumentName);
            Preconditions.checkState(x != null, "msg");
            someVeryLongObjectInstanceNameToForceWrappingOfTheMethodInvocationBeforeTheTemplate.checkState(expressionStateConditionToCheck, "short template", first, second);
            checkState(veryLongConditionExpressionToForceTheArgumentsListToWrapAndBreakEvenIfTheMethodFits, "short template", first, second);
          }
        }
        """;
    String expectedFormat =
        """
        class T {
          void f() {
            String s = String.format(
                "simple template", firstArgumentName, secondArgumentName, thirdArgumentNameLong);
            String s2 = String.format(
                "this is a very long template string to force wrapping of arguments after it",
                firstArgumentName, secondArgumentName, thirdArgumentName);
            String s3 = String.format("short fit");
            Preconditions.checkState(
                expressionStateConditionToCheck,
                "this is a very long checkState template to force wrapping",
                firstArgumentName, secondArgumentName);
            Preconditions.checkState(x != null, "msg");
            someVeryLongObjectInstanceNameToForceWrappingOfTheMethodInvocationBeforeTheTemplate.checkState(
                expressionStateConditionToCheck, "short template", first, second);
            checkState(
                veryLongConditionExpressionToForceTheArgumentsListToWrapAndBreakEvenIfTheMethodFits,
                "short template", first, second);
          }
        }
        """;
    assertThat(new Formatter().formatSource(inputFormat)).isEqualTo(expectedFormat);
  }

  @Test
  public void removeTrailingTabsInComments() throws Exception {
    assertThat(
            new Formatter()
                .formatSource(
                    "class Foo {\n"
                        + "  void f() {\n"
                        + "    int x = 0; // comment\t\t\t\n"
                        + "    return;\n"
                        + "  }\n"
                        + "}\n"))
        .isEqualTo(
            """
            class Foo {
              void f() {
                int x = 0; // comment
                return;
              }
            }
            """);
  }

  @Test
  public void testRelaxedRectangularRule_onlyArgument() throws Exception {
    String input =
        """
        class T {
          void f() {
            addArgs(Expr.newBuilder()
                .setId(1)
                .setName("foo_bar_baz_some_very_long_name_to_force_wrapping")
                .build());
          }
        }
        """;
    String expected =
        """
        class T {
          void f() {
            addArgs(Expr.newBuilder()
                .setId(1)
                .setName("foo_bar_baz_some_very_long_name_to_force_wrapping")
                .build());
          }
        }
        """;
    assertThat(new Formatter().formatSource(input)).isEqualTo(expected);
  }

  @Test
  public void testRelaxedRectangularRule_assignmentFitsFlatOnNextLine() throws Exception {
    String input =
        """
        class T {
          static final Parser<String> PLAIN_IDENTIFIER = literally(one("[a-zA-Z_]"), zeroOrMore("[a-zA-Z0-9_]")).source().suchThat(s -> !KEYWORDS.contains(s), "identifier");
        }
        """;
    String expected =
        """
        class T {
          static final Parser<String> PLAIN_IDENTIFIER =
              literally(one("[a-zA-Z_]"), zeroOrMore("[a-zA-Z0-9_]"))
                  .source()
                  .suchThat(s -> !KEYWORDS.contains(s), "identifier");
        }
        """;
    assertThat(new Formatter().formatSource(input)).isEqualTo(expected);
  }
}
