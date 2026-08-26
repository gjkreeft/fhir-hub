-- Stands in for the G-Standaard `medcode` view. Only the columns fhir-hub reads are present.
CREATE TABLE IF NOT EXISTS medcode (
	hpk BIGINT NOT NULL,
	prk BIGINT NOT NULL,
	gpk BIGINT NOT NULL
);

DELETE FROM medcode;

-- paracetamol zetpil 1000mg
INSERT INTO medcode (hpk, prk, gpk) VALUES (0, 18996, 111111);
-- oxycodon hcl tablet 5mg, known at HPK level too
INSERT INTO medcode (hpk, prk, gpk) VALUES (2106, 43800, 222222);
-- a PRK with no GPK: must not resolve
INSERT INTO medcode (hpk, prk, gpk) VALUES (0, 99999, 0);
