package io.github.oliviercailloux.minimax.strategies;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.VoterPartialPreference;
import io.github.oliviercailloux.minimax.elicitation.PSRWeights;
import io.github.oliviercailloux.minimax.elicitation.PrefKnowledge;
import io.github.oliviercailloux.minimax.elicitation.Question;
import io.github.oliviercailloux.minimax.elicitation.QuestionType;
import io.github.oliviercailloux.minimax.elicitation.QuestionVoter;
import io.github.oliviercailloux.minimax.regret.RegretComputer;

public class StrategyElitist implements Strategy {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(StrategyElitist.class);

	public static StrategyElitist newInstance() {
		return new StrategyElitist();
	}

	private StrategyHelper helper;

	private StrategyElitist() {
		helper = StrategyHelper.newInstance();
	}

	@Override
	public void setKnowledge(PrefKnowledge knowledge) {
		helper.setKnowledge(knowledge);
	}

	@Override
	public Question nextQuestion() {
		checkState(helper != null);
		final int m = helper.getKnowledge().getAlternatives().size();

		final ImmutableCollection<VoterPartialPreference> prefs = helper.getKnowledge().getProfile().values();
		for (VoterPartialPreference pref : prefs) {
			final ImmutableGraph<Alternative> graph = pref.asTransitiveGraph();
			final Set<Alternative> alternatives = graph.nodes();
			final Alternative topAlternative = alternatives.stream()
					.max(Comparator.comparing(a -> graph.successors(a).size())).get();

			if (graph.successors(topAlternative).size() < m - 1) {
				final Alternative incomparable = StrategyHelper.getIncomparables(graph, topAlternative)
						.sorted(Comparator.naturalOrder()).findFirst().get();
				return Question.toVoter(QuestionVoter.given(pref.getVoter(), topAlternative, incomparable));
			}
		}

		/** To fix: this repeats the strategy by mmr. */
		final ImmutableSet.Builder<Question> questionsBuilder = ImmutableSet.builder();
		IntStream.rangeClosed(1, m - 2).boxed()
				.forEach(i -> questionsBuilder.add(Question.toCommittee(helper.getQuestionAboutHalfRange(i))));
		final ImmutableMap<Question, MmrLottery> questions = questionsBuilder.build().stream()
				.collect(ImmutableMap.toImmutableMap(q -> q, this::toLottery));
		final Comparator<Question> questionsComparator = Comparator.comparing(questions::get,
				MmrLottery.MAX_COMPARATOR);
		final ImmutableSet<Question> bestQuestions = StrategyHelper.getMinimalElements(questions.keySet(),
				questionsComparator);
		final ImmutableMap<Question, MmrLottery> sortedQuestions = questions.keySet().stream()
				.sorted(questionsComparator).collect(ImmutableMap.toImmutableMap(q -> q, questions::get));
		LOGGER.debug("Best questions: {}.", bestQuestions);
		final Question winner = helper.sortAndDraw(bestQuestions.asList(), Comparator.naturalOrder());
		verify(winner.getType() == QuestionType.COMMITTEE_QUESTION);
		LOGGER.info("Questioning committee: {}, best lotteries: {}.", winner.asQuestionCommittee(),
				sortedQuestions.entrySet().stream().limit(6).collect(ImmutableList.toImmutableList()));

		return winner;
	}

	private double getSpread(PSRWeights wBar, PSRWeights wMin, int i) {
		return IntStream.rangeClosed(0, 2).boxed()
				.mapToDouble(k -> Math.abs(wBar.getWeightAtRank(i + k) - wMin.getWeightAtRank(i + k))).sum();
	}

	private MmrLottery toLottery(Question question) {
		final double yesMMR;
		{
			final PrefKnowledge updatedKnowledge = PrefKnowledge.copyOf(helper.getKnowledge());
			updatedKnowledge.update(question.getPositiveInformation());
			final RegretComputer rc = new RegretComputer(updatedKnowledge);
			yesMMR = rc.getMinimalMaxRegrets().getMinimalMaxRegretValue();
		}

		final double noMMR;
		{
			final PrefKnowledge updatedKnowledge = PrefKnowledge.copyOf(helper.getKnowledge());
			updatedKnowledge.update(question.getNegativeInformation());
			final RegretComputer rc = new RegretComputer(updatedKnowledge);
			noMMR = rc.getMinimalMaxRegrets().getMinimalMaxRegretValue();
		}
		final MmrLottery lottery = MmrLottery.given(yesMMR, noMMR);
		return lottery;
	}
}