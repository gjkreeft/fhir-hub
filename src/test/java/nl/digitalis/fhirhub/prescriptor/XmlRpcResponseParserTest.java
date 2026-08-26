package nl.digitalis.fhirhub.prescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.digitalis.fhirhub.error.UnauthorizedException;
import nl.digitalis.fhirhub.Fixtures;
import nl.digitalis.fhirhub.model.DrugResult;
import nl.digitalis.fhirhub.model.SessionHandle;
import nl.digitalis.fhirhub.model.SessionResult;
import nl.digitalis.fhirhub.model.SessionType;

class XmlRpcResponseParserTest {

	private final XmlRpcResponseParser parser = new XmlRpcResponseParser();

	/**
	 * The two session types read the key from different members — an upstream inconsistency
	 * that is easy to regress, so both are pinned.
	 */
	@Test
	void readsTheSessionKeyMemberThatMatchesTheSessionType() {
		String xml = Fixtures.xml("open-session-response.xml");

		SessionHandle formulary = parser.parseSessionResponse(xml, SessionType.FORMULARY);
		SessionHandle createRx = parser.parseSessionResponse(xml, SessionType.CREATE_RX);

		assertThat(formulary.sessionId()).isEqualTo("sess-abc-123");
		assertThat(createRx.sessionId()).isEqualTo("rx-def-456");
		assertThat(formulary.prescriptorUrl()).startsWith("https://evs.prescriptor.nl/");
	}

	@Test
	void treatsAnXmlRpcFaultAsAnAuthenticationFailure() {
		assertThatThrownBy(() -> parser.parseSessionResponse(Fixtures.xml("fault-response.xml"), SessionType.FORMULARY))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void parsesDrugsAndAdvices() {
		SessionResult result = parser.parseResultResponse(Fixtures.xml("request-result-response.xml"));

		assertThat(result.drugs()).hasSize(2);
		assertThat(result.advices()).hasSize(2);

		DrugResult paracetamol = result.drugs().getFirst();
		assertThat(paracetamol.codes()).singleElement().satisfies(code -> {
			assertThat(code.type()).isEqualTo("PRK");
			assertThat(code.value()).isEqualTo(18996);
			assertThat(code.quantity()).isEqualByComparingTo("15");
			assertThat(code.unit()).isEqualTo("ST");
			assertThat(code.directions().coded()).isEqualTo("3-4D1S; gedurende max. 1 maand");
		});
		assertThat(paracetamol.durationDays()).isEqualTo(4);
		assertThat(paracetamol.atc()).isEqualTo("N02BE01");
		assertThat(paracetamol.opium()).isFalse();
	}

	@Test
	void mapsPrescriptionTypeSevenToHpkAndFlagsOpium() {
		SessionResult result = parser.parseResultResponse(Fixtures.xml("request-result-response.xml"));

		DrugResult oxycodon = result.drugs().get(1);
		assertThat(oxycodon.codes().getFirst().type()).isEqualTo("HPK");
		assertThat(oxycodon.codes().getFirst().value()).isEqualTo(2106);
		assertThat(oxycodon.opium()).isTrue();
	}

	@Test
	void distinguishesLinkAdviceFromProse() {
		SessionResult result = parser.parseResultResponse(Fixtures.xml("request-result-response.xml"));

		assertThat(result.advices().getFirst().uriList()).isFalse();
		assertThat(result.advices().getFirst().text()).contains("chronische pijn").contains("&");
		assertThat(result.advices().get(1).uriList()).isTrue();
	}

	/** A nil result means the key is unknown, or the session was already consumed by an earlier poll. */
	@Test
	void treatsANilResultAsAnUnknownSession() {
		assertThatThrownBy(() -> parser.parseResultResponse(Fixtures.xml("nil-response.xml")))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("session");
	}
}
