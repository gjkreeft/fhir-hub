package nl.digitalis.fhirhub.model;

import java.util.List;

/** Everything a finished session produced. Requesting it ends the session upstream. */
public record SessionResult(List<DrugResult> drugs, List<AdviceResult> advices) {
}
