package com.github.dylon.liblevenshtein.collection.dawg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import com.google.common.base.Joiner;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dylon.liblevenshtein.collection.dawg.factory.DawgFactory;
import com.github.dylon.liblevenshtein.collection.dawg.factory.DawgNodeFactory;
import com.github.dylon.liblevenshtein.collection.dawg.factory.PrefixFactory;
import com.github.dylon.liblevenshtein.collection.dawg.factory.TransitionFactory;
import com.github.dylon.liblevenshtein.serialization.BytecodeSerializer;
import com.github.dylon.liblevenshtein.serialization.ProtobufSerializer;
import com.github.dylon.liblevenshtein.serialization.Serializer;
import static com.github.dylon.liblevenshtein.assertion.SetAssertions.assertThat;

public class DawgTest {
  private List<String> terms;
  private DawgNodeFactory dawgNodeFactory;
  private PrefixFactory<DawgNode> prefixFactory;
  private TransitionFactory<DawgNode> transitionFactory;
  private DawgFactory dawgFactory;
  private AbstractDawg emptyDawg;
  private AbstractDawg fullDawg;

  @BeforeClass
  public void setUp() throws IOException {
    try (final BufferedReader reader = new BufferedReader(
          new InputStreamReader(
            getClass().getResourceAsStream("/resources/wordsEn.txt"),
            StandardCharsets.UTF_8))) {

      final List<String> termsList = new ArrayList<>();

      String term;
      while ((term = reader.readLine()) != null) {
        termsList.add(term);
      }

      Collections.sort(termsList);

      this.terms = termsList;
      this.dawgNodeFactory = new DawgNodeFactory();
      this.prefixFactory = new PrefixFactory<>();
      this.transitionFactory = new TransitionFactory<>();
      this.dawgFactory = new DawgFactory(dawgNodeFactory, prefixFactory, transitionFactory);
      this.emptyDawg = dawgFactory.build(new ArrayList<>(0));
      this.fullDawg = dawgFactory.build(termsList);
    }
  }

  @DataProvider(name="terms")
  public Iterator<Object[]> terms() {
    return new TermIterator(terms.iterator());
  }

  @Test(dataProvider="terms")
  public void emptyDawgAcceptsNothing(final String term) {
    assertThat(emptyDawg).doesNotContain(term);
  }

  @Test(dataProvider="terms")
  public void dawgAcceptsAllItsTerms(final String term) {
    assertThat(fullDawg).contains(term);
  }

  @DataProvider(name="serializers")
  public Iterator<Object[]> serializers() {
    final List<Object[]> serializers = new LinkedList<>();
    serializers.add(new Object[] {new BytecodeSerializer()});
    serializers.add(new Object[] {new ProtobufSerializer()});
    return serializers.iterator();
  }

  @Test(dataProvider="serializers")
  public void testSerialization(final Serializer serializer) throws Exception {
    byte[] bytes;

    bytes = serializer.serialize(fullDawg);
    final AbstractDawg actualDawg =
      serializer.deserialize(SortedDawg.class, bytes);
    assertThat(actualDawg).isEqualTo(fullDawg);

    final int numIters = 50;
    double buildTime = 0.0;
    double deserializationTime = 0.0;

    for (int iter = 0; iter < numIters; iter += 1) {
      System.out.printf("::: Timing (de)serialization in iteration [%d] of [%d]%n",
        iter, numIters);

      long startTime = System.currentTimeMillis();
      final AbstractDawg dawg = dawgFactory.build(terms);
      long stopTime = System.currentTimeMillis();
      buildTime += (stopTime - startTime);

      bytes = serializer.serialize(dawg);
      startTime = System.currentTimeMillis();
      serializer.deserialize(SortedDawg.class, bytes);
      stopTime = System.currentTimeMillis();
      deserializationTime += (stopTime - startTime);
    }

    buildTime /= numIters;
    deserializationTime /= numIters;
    System.out.printf(
      "::: Deserialized DAWG in %.7f %% the average time required to build it "+
      "after [%d] iterations, using deserializer [%s]%n",
      (100.0 * deserializationTime / buildTime), numIters, serializer.getClass());
    assertThat(deserializationTime).isLessThan(buildTime);
  }

  @Test
  public void dawgAcceptsNoTermsItDoesNotContain() {
    assertThat(fullDawg).doesNotContain("", "foobar", "C+");
  }

  @Test
  public void dawgSizeIsSameAsTerms() {
    assertThat(emptyDawg)
      .isEmpty()
      .hasSize(0);
    assertThat(fullDawg)
      .isNotEmpty()
      .hasSize(terms.size());
  }

  @Test
  public void dawgAcceptsEmptyStringIfInTerms() {
    final List<String> termsList = new ArrayList<>(1);
    termsList.add("");
    final AbstractDawg dawg = dawgFactory.build(termsList);
    assertThat(dawg).contains("");
  }

  @Test
  public void dawgShouldIterateOverAllTerms() {
    final Set<String> termsList = new HashSet<>(this.terms);
    for (final String term : fullDawg) {
      try {
        assertThat(termsList).contains(term);
      }
      catch (final AssertionError exception) {
        throw new AssertionError("Expected terms to contain: \"" + term + "\"", exception);
      }
      termsList.remove(term);
    }
    if (!termsList.isEmpty()) {
      final String message =
        String.format("Expected all terms to be iterated over, but missed [%s]",
          Joiner.on(", ").join(termsList));
      throw new AssertionError(message);
    }
  }

  @Test(expectedExceptions=IllegalArgumentException.class)
  public void insertingTermsOutOfOrderShouldThrowAnException() {
    final List<String> termsList = new ArrayList<>(3);
    termsList.add("a");
    termsList.add("c");
    termsList.add("b");
    dawgFactory.build(termsList, true);
  }

  @Test
  public void equivalentDawgsShouldBeEqual() {
    final AbstractDawg other = dawgFactory.build(terms);
    assertThat(fullDawg).isEqualTo(other);
  }

  @RequiredArgsConstructor
  private static class TermIterator implements Iterator<Object[]> {
    private final Iterator<String> terms;
    private Object[] params = null;
    private Object[] buffer = new Object[1];

    @Override
    public boolean hasNext() {
      advance();
      return null != params;
    }

    @Override
    public Object[] next() {
      advance();
      final Object[] paramsLocal = this.params;
      this.params = null;
      return paramsLocal;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    public void advance() {
      if (null == params && terms.hasNext()) {
        buffer[0] = terms.next();
        params = buffer;
      }
    }
  }
}
