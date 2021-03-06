--<ScriptOptions statementTerminator=";"/>

CREATE TABLE IDENTITYPROVIDERS (
		ID VARCHAR2(255) NOT NULL,
		CERTIFICATEURL VARCHAR2(255),
		ENTITYID VARCHAR2(255),
		LOGINURL VARCHAR2(255),
		LOGOUTURL VARCHAR2(255),
		METADATA CLOB,
		METADATAURL VARCHAR2(255),
		VALIDFROM DATE,
		VALIDTO DATE,
		VERSION NUMBER(10 , 0)
	);

CREATE UNIQUE INDEX SYS_C0071006 ON IDENTITYPROVIDERS (ID ASC);

ALTER TABLE IDENTITYPROVIDERS ADD CONSTRAINT SYS_C0071006 PRIMARY KEY (ID);

