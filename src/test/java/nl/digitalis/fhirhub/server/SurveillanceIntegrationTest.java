package nl.digitalis.fhirhub.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.OperationDefinition;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import nl.digitalis.fhirhub.Fixtures;
import nl.digitalis.fhirhub.fhir.Profiles;
import nl.digitalis.fhirhub.fhir.Systems;

/**
 * The medication-surveillance base, over real HTTP.
 *
 * <p>Everything here is about a contract that is published and <em>not implemented</em>, which is
 * an unusual thing to pin and the reason it needs pinning. Three claims the published guide makes
 * would otherwise be untested: that the two bases are separate contracts and neither advertises
 * the other's operations, that a conformant request is refused with a status no client can mistake
 * for a result, and that the request profile is enforced today so an integrator can build against
 * it before the check exists.
 *
 * <p>No WireMock: this operation reaches nothing upstream, which is the whole point.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SurveillanceIntegrationTest {

	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	@Autowired
	private FhirContext fhirContext;

	@LocalServerPort
	private int port;

	private IParser parser;

	@BeforeEach
	void parser() {
		parser = fhirContext.newJsonParser();
	}

	/**
	 * The 501 is the contract. An empty Bundle of findings would be indistinguishable from a
	 * genuine all-clear, so a caller has to be given a status it cannot read as a result — and the
	 * issue code has to carry it too, because the change policy lets the wording move in a patch.
	 */
	@Test
	void refusesAConformantRequestWithANotImplementedOutcome() {
		HttpResponse<String> response = postFhir("/fhir/surveillance/$check-medication",
				surveillanceParameters());

		assertThat(response.statusCode()).isEqualTo(501);

		OperationOutcome outcome = parser.parseResource(OperationOutcome.class, response.body());
		assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTSUPPORTED);
		assertThat(outcome.getIssueFirstRep().getDiagnostics())
				.as("the response says outright that nothing may be concluded from it")
				.contains("not yet implemented")
				.contains("No conclusion");
	}

	/**
	 * The reason the request profile is enforced on an operation that cannot succeed: a host can
	 * find out its payload is wrong now rather than on the day the check goes live. A 400 and a
	 * 501 are different answers and both are useful.
	 */
	@Test
	void validatesTheRequestEvenThoughTheOperationDoesNothing() {
		Parameters in = surveillanceParameters();
		in.getParameter().removeIf(p -> "xisId".equals(p.getName()));

		HttpResponse<String> response = postFhir("/fhir/surveillance/$check-medication", in);

		assertThat(response.statusCode()).isEqualTo(400);
		OperationOutcome outcome = parser.parseResource(OperationOutcome.class, response.body());
		assertThat(outcome.getIssueFirstRep().getDiagnostics()).contains("xisId");
	}

	/** Neither a prescription to check nor a medication list to check it against. */
	@Test
	void refusesARequestWithNothingToCheck() {
		Parameters in = surveillanceParameters();
		in.getParameter().removeIf(p -> "prescription".equals(p.getName()));

		HttpResponse<String> response = postFhir("/fhir/surveillance/$check-medication", in);

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("fhirhub-something-to-check");
	}

	/**
	 * The two bases are separate contracts, and this is what notices if a provider ever extends
	 * the wrong marker: a session operation advertised on the surveillance base would tell an
	 * integrator to post patient data to an address that cannot serve it.
	 */
	@Test
	void theSurveillanceBaseAdvertisesOnlyItsOwnOperations() {
		CapabilityStatement statement = parser.parseResource(CapabilityStatement.class,
				getAnonymous("/fhir/surveillance/metadata").body());

		assertThat(operationNames(statement)).containsExactly("check-medication");
		assertThat(statement.getSoftware().getVersion())
				.as("both bases report the release of the one Implementation Guide")
				.matches("\\d+\\.\\d+\\.\\d+");
		assertThat(statement.getImplementation().getDescription())
				.contains("medication-surveillance contract");
	}

	@Test
	void theEvsBaseDoesNotAdvertiseTheSurveillanceOperation() {
		CapabilityStatement statement = parser.parseResource(CapabilityStatement.class,
				getAnonymous("/fhir/evs/metadata").body());

		assertThat(operationNames(statement))
				.containsExactlyInAnyOrder("formulary-session", "createrx-session", "session-result");
	}

	/** Discovery before credentials, on this base as on the other one. */
	@Test
	void servesItsCapabilityStatementUnauthenticatedAndNothingElse() {
		assertThat(getAnonymous("/fhir/surveillance/metadata").statusCode()).isEqualTo(200);

		HttpResponse<String> refused = send(HttpRequest
				.newBuilder(URI.create(url("/fhir/surveillance/$check-medication")))
				.header("Content-Type", "application/fhir+json")
				.POST(HttpRequest.BodyPublishers.ofString(
						parser.encodeResourceToString(surveillanceParameters())))
				.build());

		assertThat(refused.statusCode()).isEqualTo(401);
		assertThat(refused.headers().firstValue("WWW-Authenticate")).get().asString().startsWith("Basic");
	}

	/**
	 * The generated {@code OperationDefinition} is most of what makes an unimplemented operation
	 * worth publishing: it is the parameter list an integrator can generate a request from, and
	 * the published guide names both its address and its contents. Readable unauthenticated, like
	 * the statement that advertises it.
	 */
	@Test
	void describesItsParametersInAnOperationDefinitionAnyoneCanRead() {
		CapabilityStatement statement = parser.parseResource(CapabilityStatement.class,
				getAnonymous("/fhir/surveillance/metadata").body());
		String definition = statement.getRestFirstRep().getOperationFirstRep().getDefinition();

		assertThat(definition)
				.as("the address the Implementation Guide tells integrators to fetch")
				.endsWith("/fhir/surveillance/OperationDefinition/-s-check-medication");

		HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(definition)).GET().build());
		assertThat(response.statusCode()).isEqualTo(200);

		OperationDefinition operation = parser.parseResource(OperationDefinition.class, response.body());
		assertThat(operation.getParameter())
				.extracting(p -> p.getName() + " " + p.getMin() + ".." + p.getMax() + " " + p.getType())
				.containsExactly(
						"patient 1..1 Patient",
						"xisId 1..1 string",
						"xisVersion 1..1 string",
						"prescription 0..* MedicationRequest",
						"medicationStatement 0..* MedicationStatement",
						"allergyIntolerance 0..* AllergyIntolerance",
						"condition 0..* Condition",
						"observation 0..* Observation");

		// No `use: out` parameter, which is HAPI's doing rather than a decision here — the two
		// session operations describe their responses the same way, which is to say not at all.
		// The response shape lives in the profiles, and surveillance has none yet.
	}

	/** The profile is the one the service names in its refusal, so an integrator can go read it. */
	@Test
	void namesTheProfileItValidatedAgainst() {
		HttpResponse<String> response = postFhir("/fhir/surveillance/$check-medication",
				surveillanceParameters());

		assertThat(response.body()).contains(Profiles.SURVEILLANCE_INPUT);
	}

	private List<String> operationNames(CapabilityStatement statement) {
		return statement.getRest().stream()
				.map(CapabilityStatementRestComponent::getOperation)
				.flatMap(List::stream)
				.map(CapabilityStatementRestResourceOperationComponent::getName)
				.toList();
	}

	/**
	 * The payload of the published example, minus the resources that are identical to the session
	 * ones: a patient, the two identifying strings and one prescription to check.
	 */
	private Parameters surveillanceParameters() {
		Patient patient = new Patient();
		patient.setGender(AdministrativeGender.FEMALE);
		patient.setBirthDateElement(new DateType("1980-01-01"));

		MedicationRequest prescription = new MedicationRequest();
		prescription.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
		prescription.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
		prescription.setSubject(absentReference());
		prescription.getMedicationCodeableConcept().addCoding()
				.setSystem(Systems.PRK)
				.setCode("18996");

		Parameters parameters = new Parameters();
		parameters.addParameter().setName("patient").setResource(patient);
		parameters.addParameter().setName("xisId").setValue(new StringType("xis-001"));
		parameters.addParameter().setName("xisVersion").setValue(new StringType("1.0"));
		parameters.addParameter().setName("prescription").setResource(prescription);

		return parameters;
	}

	/** Mandatory in base R4, never read here — the same idiom the session examples use. */
	private Reference absentReference() {
		Reference reference = new Reference();
		reference.addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason",
				new CodeType("unknown"));

		return reference;
	}

	private HttpResponse<String> postFhir(String path, Parameters body) {
		return send(HttpRequest.newBuilder(URI.create(url(path)))
				.header("Authorization", "Basic " + encode(
						Fixtures.CREDENTIALS.practiceId() + ":" + Fixtures.CREDENTIALS.licenseKey()))
				.header("Content-Type", "application/fhir+json")
				.POST(HttpRequest.BodyPublishers.ofString(parser.encodeResourceToString(body)))
				.build());
	}

	private HttpResponse<String> getAnonymous(String path) {
		return send(HttpRequest.newBuilder(URI.create(url(path))).GET().build());
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private static String encode(String pair) {
		return Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
	}
}
