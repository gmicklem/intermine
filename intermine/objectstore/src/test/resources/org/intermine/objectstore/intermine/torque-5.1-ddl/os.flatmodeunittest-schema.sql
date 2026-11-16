
-----------------------------------------------------------------------------
-- Broke
-----------------------------------------------------------------------------
DROP TABLE Broke CASCADE;


CREATE TABLE Broke
(
    interestRate DOUBLE PRECISION NOT NULL,
    id INTEGER NOT NULL,
    debt INTEGER NOT NULL,
    bankId INTEGER,
    owedById INTEGER,
    CONSTRAINT Broke_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Employee
-----------------------------------------------------------------------------
DROP TABLE Employee CASCADE;


CREATE TABLE Employee
(
    id INTEGER NOT NULL,
    title TEXT,
    seniority INTEGER,
    salary INTEGER NOT NULL,
    name TEXT,
    age INTEGER NOT NULL,
    intermine_end TEXT,
    fullTime BOOLEAN NOT NULL,
    employmentPeriodId INTEGER,
    addressId INTEGER,
    departmentId INTEGER,
    companyId INTEGER,
    departmentThatRejectedMeId INTEGER,
    class TEXT,
    tableclass TEXT,
    CONSTRAINT Employee_pkey UNIQUE (id, tableclass)
);


-----------------------------------------------------------------------------
-- Department
-----------------------------------------------------------------------------
DROP TABLE Department CASCADE;


CREATE TABLE Department
(
    id INTEGER NOT NULL,
    name TEXT,
    managerId INTEGER,
    companyId INTEGER,
    CONSTRAINT Department_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Company
-----------------------------------------------------------------------------
DROP TABLE Company CASCADE;


CREATE TABLE Company
(
    id INTEGER NOT NULL,
    name TEXT,
    vatNumber INTEGER NOT NULL,
    bankId INTEGER,
    addressId INTEGER,
    CEOId INTEGER,
    CONSTRAINT Company_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Address
-----------------------------------------------------------------------------
DROP TABLE Address CASCADE;


CREATE TABLE Address
(
    id INTEGER NOT NULL,
    address TEXT,
    CONSTRAINT Address_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- ImportantPerson
-----------------------------------------------------------------------------
DROP TABLE ImportantPerson CASCADE;


CREATE TABLE ImportantPerson
(
    id INTEGER NOT NULL,
    title TEXT,
    seniority INTEGER,
    salary INTEGER NOT NULL,
    name TEXT,
    age INTEGER NOT NULL,
    intermine_end TEXT,
    fullTime BOOLEAN NOT NULL,
    employmentPeriodId INTEGER,
    personalAddressId INTEGER,
    addressId INTEGER,
    departmentId INTEGER,
    companyId INTEGER,
    businessAddressId INTEGER,
    departmentThatRejectedMeId INTEGER,
    class TEXT,
    tableclass TEXT,
    CONSTRAINT ImportantPerson_pkey UNIQUE (id, tableclass)
);


-----------------------------------------------------------------------------
-- Secretary
-----------------------------------------------------------------------------
DROP TABLE Secretary CASCADE;


CREATE TABLE Secretary
(
    id INTEGER NOT NULL,
    name TEXT,
    CONSTRAINT Secretary_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Types
-----------------------------------------------------------------------------
DROP TABLE Types CASCADE;


CREATE TABLE Types
(
    longObjType INT8,
    floatType REAL NOT NULL,
    shortObjType INT2,
    id INTEGER NOT NULL,
    shortType INT2 NOT NULL,
    name TEXT,
    intType INTEGER NOT NULL,
    longType INT8 NOT NULL,
    clobObjType TEXT,
    floatObjType REAL,
    booleanObjType BOOLEAN,
    dateObjType INT8,
    doubleObjType DOUBLE PRECISION,
    stringObjType TEXT,
    bigDecimalObjType NUMERIC,
    booleanType BOOLEAN NOT NULL,
    doubleType DOUBLE PRECISION NOT NULL,
    intObjType INTEGER,
    CONSTRAINT Types_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Bank
-----------------------------------------------------------------------------
DROP TABLE Bank CASCADE;


CREATE TABLE Bank
(
    id INTEGER NOT NULL,
    name TEXT,
    CONSTRAINT Bank_pkey UNIQUE (id)
);


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
-- EmploymentPeriod
-----------------------------------------------------------------------------
DROP TABLE EmploymentPeriod CASCADE;


CREATE TABLE EmploymentPeriod
(
    id INTEGER NOT NULL,
    startDate INT8,
    endDate INT8,
    CONSTRAINT EmploymentPeriod_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- intermine_Range
-----------------------------------------------------------------------------
DROP TABLE intermine_Range CASCADE;


CREATE TABLE intermine_Range
(
    rangeEnd INTEGER NOT NULL,
    id INTEGER NOT NULL,
    rangeStart INTEGER NOT NULL,
    name TEXT,
    parentId INTEGER,
    CONSTRAINT intermine_Range_pkey UNIQUE (id)
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
-- Broke                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Employee                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Department                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Company                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Address                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- ImportantPerson                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Secretary                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Types                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Bank                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- SimpleObject                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- EmploymentPeriod                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- intermine_Range                                                      
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

