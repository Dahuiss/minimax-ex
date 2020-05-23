package io.github.oliviercailloux.minimax;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apfloat.Apint;
import org.apfloat.Aprational;
import org.apfloat.AprationalMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.graph.Graph;

import io.github.oliviercailloux.minimax.elicitation.PrefKnowledge;
import io.github.oliviercailloux.minimax.elicitation.Question;
import io.github.oliviercailloux.minimax.elicitation.QuestionCommittee;
import io.github.oliviercailloux.y2018.j_voting.Alternative;
import io.github.oliviercailloux.y2018.j_voting.Voter;

public class StrategyRandom implements Strategy {

	private static final Logger LOGGER = LoggerFactory.getLogger(StrategyRandom.class);

	public static StrategyRandom build() {
		return new StrategyRandom();
	}

	private PrefKnowledge knowledge;

	private Random random;

	private StrategyRandom() {
		final long seed = ThreadLocalRandom.current().nextLong();
		LOGGER.info("Random. Using seed: {}.", seed);
		random = new Random(seed);
	}

	void setRandom(Random random) {
		this.random = requireNonNull(random);
	}

	@Override
	public Question nextQuestion() {
		final int m = knowledge.getAlternatives().size();
		Verify.verify(m > 2 || (m == 2 && !knowledge.isProfileComplete()));
		final ImmutableSet.Builder<Voter> questionableVotersBuilder = ImmutableSet.builder();
		for (Voter voter : knowledge.getVoters()) {
			final Graph<Alternative> graph = knowledge.getProfile().get(voter).asTransitiveGraph();
			if (graph.edges().size() != m * (m - 1) / 2) {
				questionableVotersBuilder.add(voter);
			}
		}
		final ImmutableSet<Voter> questionableVoters = questionableVotersBuilder.build();

		final ArrayList<Integer> candidateRanks = IntStream.rangeClosed(1, m - 2).boxed()
				.collect(Collectors.toCollection(ArrayList::new));
		Collections.shuffle(candidateRanks, random);
		QuestionCommittee qc = null;
		for (int rank : candidateRanks) {
			final Range<Aprational> lambdaRange = knowledge.getLambdaRange(rank);
			final Aprational avg = AprationalMath.sum(lambdaRange.lowerEndpoint(), lambdaRange.upperEndpoint())
					.divide(new Apint(2));
			qc = QuestionCommittee.given(avg, rank);
		}

		final boolean existsQuestionWeight = qc != null;
		final boolean existsQuestionVoters = !questionableVoters.isEmpty();

		assert (existsQuestionWeight || existsQuestionVoters);

		final boolean aboutWeight;
		if (!existsQuestionWeight) {
			aboutWeight = false;
		} else if (!existsQuestionVoters) {
			aboutWeight = true;
		} else {
			aboutWeight = random.nextBoolean();
		}

		final Question q;

		if (aboutWeight) {
			assert m >= 3;
			assert qc != null;
			q = Question.toCommittee(qc);
		} else {
			assert !questionableVoters.isEmpty();
			final int idx = random.nextInt(questionableVoters.size());
			final Voter voter = questionableVoters.asList().get(idx);
			final ArrayList<Alternative> altsRandomOrder = new ArrayList<>(knowledge.getAlternatives());
			Collections.shuffle(altsRandomOrder, random);
			final Graph<Alternative> graph = knowledge.getProfile().get(voter).asTransitiveGraph();
			final Optional<Alternative> withIncomparabilities = altsRandomOrder.stream()
					.filter((a1) -> graph.adjacentNodes(a1).size() != m - 1).findAny();
			assert withIncomparabilities.isPresent();
			final Alternative a1 = withIncomparabilities.get();
			final Optional<Alternative> incomparable = altsRandomOrder.stream()
					.filter((a2) -> !a1.equals(a2) && !graph.adjacentNodes(a1).contains(a2)).findAny();
			assert incomparable.isPresent();
			final Alternative a2 = incomparable.get();
			q = Question.toVoter(voter, a1, a2);
		}
		return q;
	}

	@Override
	public void setKnowledge(PrefKnowledge knowledge) {
		this.knowledge = knowledge;
	}

	@Override
	public String toString() {
		return "Random";
	}
}
