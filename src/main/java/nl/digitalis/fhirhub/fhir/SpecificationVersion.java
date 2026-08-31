package nl.digitalis.fhirhub.fhir;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.hl7.fhir.r4.model.StructureDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;

/**
 * Which release of the published specification this build implements.
 *
 * <p>The Implementation Guide at {@code http://spec.digitalis.nl/fhir} versions the contract, and
 * its change policy tells integrators two things that only work if a deployment can be asked
 * which release it is running: that a new parameter name is additive for this service but a 400
 * for a host that starts sending it too early — the inbound slicing is closed — and that
 * validating against a release the deployment has moved past passes on the old rules. So
 * {@code GET /fhir/evs/metadata} reports this number, and it is the answer to "which one are you".
 *
 * <p>It is read off the profiles in the jar rather than declared here, because those are the
 * artifacts that decide what the service actually accepts. A number kept anywhere else would be a
 * second copy, and the copy that was wrong would be the one integrators read.
 * {@code ig/scripts/stamp-version.mjs} is what puts the version on them and
 * {@code IgCanonicalsTest.theProfilesCarryTheIgVersion} is what notices when it has not run.
 *
 * <p>Not the application version: {@code pom.xml} versions the deployable, and two releases of
 * that can implement the same specification.
 */
@Component
public class SpecificationVersion {

	/** Where the build puts {@code ig/fsh-generated/resources}. */
	private static final String PROFILE_DIRECTORY = "fhir-profiles/";

	private final String version;

	SpecificationVersion(FhirContext fhirContext) throws IOException {
		this.version = readVersion(fhirContext, PROFILE_DIRECTORY + fileName(Profiles.FORMULARY_SESSION_INPUT));
	}

	/** The version of the Implementation Guide, e.g. {@code 0.1.0}. */
	public String version() {
		return version;
	}

	/**
	 * A profile canonical ends in the SUSHI id, and the build writes the file under that id — so
	 * the filename follows the constant rather than being repeated next to it.
	 */
	private static String fileName(String canonical) {
		return "StructureDefinition-" + canonical.substring(canonical.lastIndexOf('/') + 1) + ".json";
	}

	private static String readVersion(FhirContext fhirContext, String path) throws IOException {
		Resource profile = new ClassPathResource(path);

		if (!profile.exists()) {
			// The same failure FhirValidationConfig refuses to start on, for the same reason: a
			// build without the IG is a build that enforces nothing.
			throw new IllegalStateException(
					"No profile at classpath:" + path + ". Run 'npm run sushi' in ig/ and rebuild.");
		}

		try (Reader reader = new InputStreamReader(profile.getInputStream(), StandardCharsets.UTF_8)) {
			String version = fhirContext.newJsonParser()
					.parseResource(StructureDefinition.class, reader)
					.getVersion();

			if (version == null || version.isBlank()) {
				// SUSHI stops stamping `version` once it is generating a real IG and the
				// publisher applies it into ig/output only, so an unstamped profile is what an
				// omitted `npm run sushi` looks like.
				throw new IllegalStateException("classpath:" + path
						+ " carries no version. Run 'npm run sushi' in ig/ so stamp-version.mjs runs, and rebuild.");
			}

			return version;
		}
	}
}
