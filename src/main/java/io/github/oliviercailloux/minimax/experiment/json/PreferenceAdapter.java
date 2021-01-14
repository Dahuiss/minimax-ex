package io.github.oliviercailloux.minimax.experiment.json;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.Voter;
import io.github.oliviercailloux.j_voting.VoterStrictPreference;

public class PreferenceAdapter implements JsonbAdapter<VoterStrictPreference, JsonObject> {
	public static final PreferenceAdapter INSTANCE = new PreferenceAdapter();

	@Override
	public JsonObject adaptToJson(VoterStrictPreference obj) {
		return JsonConverter.toJson(obj);
	}

	@Override
	public VoterStrictPreference adaptFromJson(JsonObject obj) {
		final JsonArray preference = obj.getJsonArray("preference");
		final ImmutableList<Alternative> alternatives = preference.stream().map(v -> ((JsonNumber) v).intValue())
				.map(Alternative::withId).collect(ImmutableList.toImmutableList());
		return VoterStrictPreference.given(Voter.withId(obj.getInt("voter")), alternatives);
	}
}
