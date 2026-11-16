
-----------------------------------------------------------------------------
-- SimpleObject
-----------------------------------------------------------------------------
DROP TABLE SimpleObject CASCADE;


CREATE TABLE SimpleObject
(
    name TEXT,
    employeeId INTEGER
);


-----------------------------------------------------------------------------
-- InterMineObject
-----------------------------------------------------------------------------
DROP TABLE InterMineObject CASCADE;


CREATE TABLE InterMineObject
(
    OBJECT TEXT,
    floatType REAL NOT NULL,
    shortObjType INT2,
    rangeStart INTEGER NOT NULL,
    age INTEGER NOT NULL,
    fullTime BOOLEAN NOT NULL,
    interestRate DOUBLE PRECISION NOT NULL,
    longType INT8 NOT NULL,
    clobObjType TEXT,
    startDate INT8,
    doubleObjType DOUBLE PRECISION,
    debt INTEGER NOT NULL,
    endDate INT8,
    intObjType INTEGER,
    longObjType INT8,
    rangeEnd INTEGER NOT NULL,
    id INTEGER NOT NULL,
    title TEXT,
    shortType INT2 NOT NULL,
    address TEXT,
    name TEXT,
    vatNumber INTEGER NOT NULL,
    intermine_end TEXT,
    intType INTEGER NOT NULL,
    floatObjType REAL,
    booleanObjType BOOLEAN,
    seniority INTEGER,
    dateObjType INT8,
    salary INTEGER NOT NULL,
    stringObjType TEXT,
    bigDecimalObjType NUMERIC,
    doubleType DOUBLE PRECISION NOT NULL,
    booleanType BOOLEAN NOT NULL,
    parentId INTEGER,
    personalAddressId INTEGER,
    owedById INTEGER,
    departmentId INTEGER,
    CEOId INTEGER,
    businessAddressId INTEGER,
    departmentThatRejectedMeId INTEGER,
    employmentPeriodId INTEGER,
    bankId INTEGER,
    managerId INTEGER,
    addressId INTEGER,
    companyId INTEGER,
    class TEXT,
    tableclass TEXT,
    CONSTRAINT InterMineObject_pkey UNIQUE (id, tableclass)
);


-----------------------------------------------------------------------------
-- OldComsOldContracts
-----------------------------------------------------------------------------
DROP TABLE OldComsOldContracts CASCADE;


CREATE TABLE OldComsOldContracts
(
    OldContracts INTEGER NOT NULL,
    OldComs INTEGER NOT NULL
);


-----------------------------------------------------------------------------
-- CompanysContractors
-----------------------------------------------------------------------------
DROP TABLE CompanysContractors CASCADE;


CREATE TABLE CompanysContractors
(
    Contractors INTEGER NOT NULL,
    Companys INTEGER NOT NULL
);


-----------------------------------------------------------------------------
-- HasSecretarysSecretarys
-----------------------------------------------------------------------------
DROP TABLE HasSecretarysSecretarys CASCADE;


CREATE TABLE HasSecretarysSecretarys
(
    HasSecretarys INTEGER NOT NULL,
    Secretarys INTEGER NOT NULL
);


-----------------------------------------------------------------------------
-- intermine_metadata
-----------------------------------------------------------------------------
DROP TABLE intermine_metadata CASCADE;


CREATE TABLE intermine_metadata
(
    key TEXT,
    value TEXT,
    blob_value BYTEA,
    CONSTRAINT intermine_metadata_key UNIQUE (key)
);


-----------------------------------------------------------------------------
-- osbag_int
-----------------------------------------------------------------------------
DROP TABLE osbag_int CASCADE;


CREATE TABLE osbag_int
(
    bagid INTEGER,
    value INTEGER,
    CONSTRAINT osbag_int_index1 UNIQUE (bagid, value)
);
CREATE INDEX osbag_int_index2 ON osbag_int (value, bagid);


-----------------------------------------------------------------------------
-- clob
-----------------------------------------------------------------------------
DROP TABLE clob CASCADE;


CREATE TABLE clob
(
    clobid INTEGER,
    clobpage INTEGER,
    value TEXT,
    CONSTRAINT clob_index UNIQUE (clobid, clobpage)
);


----------------------------------------------------------------------
-- clob                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- SimpleObject                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- InterMineObject                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- OldComsOldContracts                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- CompanysContractors                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- HasSecretarysSecretarys                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- intermine_metadata                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- osbag_int                                                      
----------------------------------------------------------------------

