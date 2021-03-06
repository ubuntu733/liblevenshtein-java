package com.github.liblevenshtein.transducer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import it.unimi.dsi.fastutil.chars.CharIterator;

import lombok.NonNull;

import com.github.liblevenshtein.collection.AbstractIterator;

/**
 * <p>
 * Lazy implementation of {@link Iterable}, which in subsequent invocations of
 * its {@link #iterator()} searches only enough of the dictionary automaton to
 * find the next spelling candidate.  Subsequent calls to {@link Iterator#next()}
 * pick up where the search stopped.
 * </p>
 *
 * <p>
 * Please note that the {@link #iterator()} is not currently threadsafe, so if
 * you will be consuming the same {@link Iterator} from multiple threads you
 * will need to synchronize access yourself.
 * </p>
 *
 * <p>
 * The algorithm for imitating Levenshtein automata was taken from the
 * following journal article:
 * </p>
 *
 * <pre>
 * <code>
 * {@literal @}ARTICLE {Schulz02faststring,
 *   author = {Klaus Schulz and Stoyan Mihov},
 *   title = {Fast String Correction with Levenshtein-Automata},
 *   journal = {INTERNATIONAL JOURNAL OF DOCUMENT ANALYSIS AND RECOGNITION},
 *   year = {2002},
 *   volume = {5},
 *   pages = {67--85}
 * }
 * </code>
 * </pre>
 *
 * <p>
 * As well, this Master Thesis helped me understand its concepts:
 * </p>
 *
 * <ul>
 *   <li>www.fmi.uni-sofia.bg/fmi/logic/theses/mitankin-en.pdf</li>
 * </ul>
 *
 * <p>
 * The supervisor of the student who submitted the thesis was one of the authors
 * of the journal article, above.
 * </p>
 *
 * @author Dylon Edwards
 * @param <DictionaryNode> Kind of nodes of the dictionary automaton.
 * @param <CandidateType> Kind of the spelling candidates returned from the
 *   dictionary.
 * @since 2.1.2
 */
public class LazyTransducerCollection<DictionaryNode, CandidateType>
    extends AbstractIterator<CandidateType>
    implements Iterable<CandidateType> {

  /**
   * Query term whose spelling should be corrected.
   */
  @NonNull
  private final String term;

  /**
   * Maximum number of spelling errors candidates may have from the query term.
   */
  private final int maxDistance;

  /**
   * Attributes required for this transducer to search the dictionary.
   */
  @NonNull
  private final TransducerAttributes<DictionaryNode, CandidateType> attributes;

  /**
   * Breadth-first traversal of the dictionary automaton.
   */
  private final Deque<Intersection<DictionaryNode>> pendingQueue = new ArrayDeque<>();

  /**
   * Transitions one state to another.
   */
  private final StateTransitionFunction stateTransition;

  /**
   * Helper variable used when determining the length of a characteristic
   * vector.
   */
  private final int a;

  /**
   * Length of the next characteristic vector to return.
   */
  private int k;

  /**
   * Offset of where to begin traversing the next characteristic vector.
   */
  private int i;

  /**
   * Labels of the outgoing transitions for a dictionary state.
   */
  private CharIterator labels = null;

  /**
   * Current intersection between the dictionary and Levenshtein automata.
   */
  private Intersection<DictionaryNode> intersection = null;

  /**
   * Initializes a new LazyTransducerCollection with a query against the
   * dictionary automaton.
   * @param term Query term whose spelling should be corrected.
   * @param maxDistance Maximum number of spelling errors candidates may have
   *   from the query term.
   * @param attributes Attributes required for this transducer to search the
   *   dictionary.
   */
  public LazyTransducerCollection(
      @NonNull final String term,
      final int maxDistance,
      @NonNull final TransducerAttributes<DictionaryNode, CandidateType> attributes) {

    this.term = term;
    this.maxDistance = maxDistance;
    this.attributes = attributes;

    pendingQueue.addLast(
      new Intersection<DictionaryNode>(
        attributes.dictionaryRoot(),
        attributes.initialState()));

    this.stateTransition =
      attributes.stateTransitionFactory().build(maxDistance, term.length());

    // f(x) := x * 2 + 1
    // a := (n - 1) / 2
    // f(a) = (n - 1) / 2 * 2 + 1 = n - 1 + 1 = n
    //
    // We want to cap the value of "a" at "n = max integer" so "f(a)" does not
    // overflow, which it would if "a > (n - 1) / 2".  In other words, define:
    // g(x) := { x * 2 + 1  , if a < (n - 1) / 2
    //         { n          , otherwise
    //
    // If there was no upper bound for integer values, this would be equivalent:
    // h(x) := min {f(x), n}
    this.a = maxDistance < (Integer.MAX_VALUE - 1) >> 1
      ? (maxDistance << 1) + 1
      : Integer.MAX_VALUE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterator<CandidateType> iterator() {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void advance() {
    while (null == next
        && (null != labels && labels.hasNext() || !pendingQueue.isEmpty())) {

      if (null != labels && labels.hasNext()) {
        final DictionaryNode dictionaryNode = intersection.dictionaryNode();
        final State levenshteinState = intersection.levenshteinState();
        final char label = labels.nextChar();
        final DictionaryNode nextDictionaryNode =
          attributes.dictionaryTransition().of(dictionaryNode, label);
        final boolean[] characteristicVector =
          characteristicVector(label, term, k, i);
        final State nextLevenshteinState =
          stateTransition.of(levenshteinState, characteristicVector);
        if (null != nextLevenshteinState) {
          final Intersection<DictionaryNode> nextIntersection = new Intersection<>(
            intersection,
            label,
            nextDictionaryNode,
            nextLevenshteinState);

          pendingQueue.addLast(nextIntersection);

          if (attributes.isFinal().at(nextDictionaryNode)) {
            final int distance =
              attributes.minDistance().at(nextLevenshteinState, term.length());
            if (distance <= maxDistance) {
              final String nextCandidate = nextIntersection.candidate();
              this.next =
                attributes.candidateFactory().build(nextCandidate, distance);
            }
          }
        }
      }
      else {
        this.intersection = pendingQueue.removeFirst();
        final DictionaryNode dictionaryNode = intersection.dictionaryNode();
        final State levenshteinState = intersection.levenshteinState();

        this.i = levenshteinState.head().termIndex();
        final int b = term.length() - i;
        this.k = a < b ? a : b;
        this.labels = attributes.dictionaryTransition().of(dictionaryNode);
      }
    }
  }

  /**
   * Returns the characteristic vector of the term, from its characters between
   * index i and index k. The characteristic vector contains true at each index
   * where the corresponding character of the term is the value of x, and false
   * elsewhere.
   * @param x char to find all occurrences of in the relevant substring of term
   * @param term Term in which to find all occurrences of the character, x
   * @param k Length of the substring of term to examine
   * @param i Base-index of the substring of term to examine
   * @return Characteristic vector marking where x appears in the relevant
   *   substring of term.
   */
  private boolean[] characteristicVector(
      final char x,
      final String term,
      final int k,
      final int i) {

    final boolean[] characteristicVector = new boolean[k];

    for (int j = 0; j < k; ++j) {
      characteristicVector[j] = x == term.charAt(i + j);
    }

    return characteristicVector;
  }
}
