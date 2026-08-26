package nl.digitalis.fhirhub.prescriptor;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import nl.digitalis.fhirhub.error.UnauthorizedException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import nl.digitalis.fhirhub.model.AdviceResult;
import nl.digitalis.fhirhub.model.Directions;
import nl.digitalis.fhirhub.model.DrugCode;
import nl.digitalis.fhirhub.model.DrugResult;
import nl.digitalis.fhirhub.model.SessionHandle;
import nl.digitalis.fhirhub.model.SessionResult;
import nl.digitalis.fhirhub.model.SessionType;

/** Parses the XML-RPC responses Prescriptor returns. */
@Component
public class XmlRpcResponseParser {

	private static final String ADVICE_TYPE_PRESCRIPTION = "Prescription";
	private static final String ADVICE_TYPE_PATIENT = "Patient";
	private static final String THUISARTS_PREFIX = "https://www.thuisarts.nl";

	/** Upstream numeric prescription types, as used to pick the DrugCode/DrugName member to read. */
	private static final Map<String, String> PRESCRIPTION_TYPES = Map.of(
			"7", "HPK",
			"8", "GPK",
			"9", "PRK");

	public SessionHandle parseSessionResponse(String xml, SessionType type) {
		Element response = parse(xml).getDocumentElement();
		failOnFault(response);

		Map<String, String> members = readStruct(requireStruct(response));
		String sessionId = members.get(type.sessionKeyMember());
		String url = members.get("Url");

		if (isBlank(sessionId) || isBlank(url)) {
			throw new InternalErrorException("Prescriptor returned a session without a key or URL");
		}

		return new SessionHandle(sessionId, url);
	}

	public SessionResult parseResultResponse(String xml) {
		Element response = parse(xml).getDocumentElement();
		failOnFault(response);

		Element value = firstChild(
				firstChild(firstChild(response, "params"), "param"), "value");

		if (value == null) {
			throw new InternalErrorException("Prescriptor returned a malformed result response");
		}

		// A nil value means the session key is unknown or already consumed. Requesting a result
		// ends the session, so a second call for the same id lands here too.
		if (firstChild(value, "nil") != null) {
			throw new UnauthorizedException("No data found for the session ID");
		}

		List<DrugResult> drugs = new ArrayList<>();
		List<AdviceResult> advices = new ArrayList<>();

		Element data = firstChild(firstChild(value, "array"), "data");
		for (Element entry : children(data, "value")) {
			Element struct = firstChild(entry, "struct");
			if (struct == null) {
				continue;
			}

			Map<String, String> members = readStruct(struct);
			String adviceType = members.get("AdviceType");

			if (ADVICE_TYPE_PRESCRIPTION.equals(adviceType)) {
				drugs.add(toDrug(members));
			}
			else if (ADVICE_TYPE_PATIENT.equals(adviceType)) {
				advices.add(toAdvice(members));
			}
		}

		return new SessionResult(List.copyOf(drugs), List.copyOf(advices));
	}

	private DrugResult toDrug(Map<String, String> members) {
		String prescriptionType = PRESCRIPTION_TYPES.get(members.get("PrescriptionType"));
		if (prescriptionType == null) {
			throw new InternalErrorException(
					"Unknown PrescriptionType: " + members.get("PrescriptionType"));
		}

		DrugCode code = new DrugCode(
				prescriptionType,
				toInteger(members.get("DrugCode" + prescriptionType)),
				members.get("DrugName" + prescriptionType),
				toDecimal(members.get("NumbersSupplied")),
				members.get("SupplyUnit"),
				new Directions("tabel25", members.get("CodedDirection"), members.get("UserDirection")));

		return new DrugResult(
				List.of(code),
				toInteger(members.get("SupplyDuration")),
				"Y".equals(members.get("Opium")),
				members.get("DrugName"),
				members.get("ATC"));
	}

	private AdviceResult toAdvice(Map<String, String> members) {
		String text = members.getOrDefault("PatientAdvice", "");

		return new AdviceResult(text, text.startsWith(THUISARTS_PREFIX));
	}

	/**
	 * Flattens an XML-RPC {@code <struct>} into a plain name/value map.
	 *
	 * <p>Every member value is read as text regardless of its declared type; the callers know
	 * which fields are numeric and convert them.
	 */
	private Map<String, String> readStruct(Element struct) {
		Map<String, String> members = new LinkedHashMap<>();
		for (Element member : children(struct, "member")) {
			Element name = firstChild(member, "name");
			Element value = firstChild(member, "value");
			if (name == null || value == null) {
				continue;
			}

			members.put(name.getTextContent().trim(), typedValue(value));
		}

		return members;
	}

	/** Reads {@code <value><string>x</string></value>} and the untyped {@code <value>x</value>} alike. */
	private String typedValue(Element value) {
		for (Element typed : children(value, null)) {
			return typed.getTextContent().trim();
		}

		return value.getTextContent().trim();
	}

	private Element requireStruct(Element response) {
		Element struct = firstChild(
				firstChild(firstChild(firstChild(response, "params"), "param"), "value"), "struct");

		if (struct == null) {
			throw new InternalErrorException("Prescriptor returned a malformed response");
		}

		return struct;
	}

	private void failOnFault(Element response) {
		if (firstChild(response, "fault") != null) {
			throw new UnauthorizedException("Invalid organization ID or key");
		}
	}

	private Document parse(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// Prescriptor's XML is trusted, but the parser is hardened anyway: an upstream
			// compromise should not turn into XXE against fhir-hub.
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);

			DocumentBuilder builder = factory.newDocumentBuilder();

			return builder.parse(new InputSource(new StringReader(xml)));
		}
		catch (Exception e) {
			throw new InternalErrorException("Error parsing the Prescriptor response", e);
		}
	}

	private Element firstChild(Element parent, String name) {
		List<Element> found = children(parent, name);

		return found.isEmpty() ? null : found.getFirst();
	}

	private List<Element> children(Element parent, String name) {
		if (parent == null) {
			return List.of();
		}

		List<Element> found = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node instanceof Element element && (name == null || name.equals(element.getTagName()))) {
				found.add(element);
			}
		}

		return found;
	}

	/** NumbersSupplied is a double upstream; partial packs are real, so it is not rounded. */
	private BigDecimal toDecimal(String value) {
		if (isBlank(value)) {
			return null;
		}

		try {
			return new BigDecimal(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private Integer toInteger(String value) {
		if (isBlank(value)) {
			return null;
		}

		try {
			return Integer.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
