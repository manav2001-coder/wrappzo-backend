-- A company's profile, as MobixFL's franchise has one: who to contact, its tax and registration
-- ids, where it is, and free notes. All optional -- an existing company simply has none yet.
ALTER TABLE companies ADD COLUMN contact_person_name VARCHAR(160);
ALTER TABLE companies ADD COLUMN contact_email VARCHAR(254);
ALTER TABLE companies ADD COLUMN contact_mobile VARCHAR(20);
ALTER TABLE companies ADD COLUMN gstin VARCHAR(15);
ALTER TABLE companies ADD COLUMN pan VARCHAR(10);
ALTER TABLE companies ADD COLUMN registration_number VARCHAR(60);
ALTER TABLE companies ADD COLUMN address_line1 VARCHAR(200);
ALTER TABLE companies ADD COLUMN address_line2 VARCHAR(200);
ALTER TABLE companies ADD COLUMN city VARCHAR(100);
ALTER TABLE companies ADD COLUMN state VARCHAR(100);
ALTER TABLE companies ADD COLUMN pincode VARCHAR(10);
ALTER TABLE companies ADD COLUMN notes TEXT;
