
-----------------------------------------------------------------------------
-- Broke
-----------------------------------------------------------------------------
DROP TABLE Broke CASCADE;


CREATE TABLE Broke
(
    OBJECT TEXT,
    interestRate DOUBLE PRECISION NOT NULL,
    id INTEGER NOT NULL,
    debt INTEGER NOT NULL,
    bankId INTEGER,
    owedById INTEGER,
    class TEXT,
    CONSTRAINT Broke_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Thing
-----------------------------------------------------------------------------
DROP TABLE Thing CASCADE;


CREATE TABLE Thing
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    class TEXT,
    CONSTRAINT Thing_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Employable
-----------------------------------------------------------------------------
DROP TABLE Employable CASCADE;


CREATE TABLE Employable
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    name TEXT,
    class TEXT,
    CONSTRAINT Employable_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- HasAddress
-----------------------------------------------------------------------------
DROP TABLE HasAddress CASCADE;


CREATE TABLE HasAddress
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    addressId INTEGER,
    class TEXT,
    CONSTRAINT HasAddress_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- HasSecretarys
-----------------------------------------------------------------------------
DROP TABLE HasSecretarys CASCADE;


CREATE TABLE HasSecretarys
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    class TEXT,
    CONSTRAINT HasSecretarys_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Contractor
-----------------------------------------------------------------------------
DROP TABLE Contractor CASCADE;


CREATE TABLE Contractor
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    seniority INTEGER,
    name TEXT,
    personalAddressId INTEGER,
    businessAddressId INTEGER,
    class TEXT,
    CONSTRAINT Contractor_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Manager
-----------------------------------------------------------------------------
DROP TABLE Manager CASCADE;


CREATE TABLE Manager
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    title TEXT,
    seniority INTEGER,
    age INTEGER NOT NULL,
    name TEXT,
    intermine_end TEXT,
    fullTime BOOLEAN NOT NULL,
    employmentPeriodId INTEGER,
    addressId INTEGER,
    departmentId INTEGER,
    departmentThatRejectedMeId INTEGER,
    class TEXT,
    CONSTRAINT Manager_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Employee
-----------------------------------------------------------------------------
DROP TABLE Employee CASCADE;


CREATE TABLE Employee
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    age INTEGER NOT NULL,
    name TEXT,
    intermine_end TEXT,
    fullTime BOOLEAN NOT NULL,
    employmentPeriodId INTEGER,
    addressId INTEGER,
    departmentId INTEGER,
    departmentThatRejectedMeId INTEGER,
    class TEXT,
    CONSTRAINT Employee_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Department
-----------------------------------------------------------------------------
DROP TABLE Department CASCADE;


CREATE TABLE Department
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    name TEXT,
    managerId INTEGER,
    companyId INTEGER,
    class TEXT,
    CONSTRAINT Department_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Company
-----------------------------------------------------------------------------
DROP TABLE Company CASCADE;


CREATE TABLE Company
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    name TEXT,
    vatNumber INTEGER NOT NULL,
    bankId INTEGER,
    addressId INTEGER,
    CEOId INTEGER,
    class TEXT,
    CONSTRAINT Company_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Address
-----------------------------------------------------------------------------
DROP TABLE Address CASCADE;


CREATE TABLE Address
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    address TEXT,
    class TEXT,
    CONSTRAINT Address_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- RandomInterface
-----------------------------------------------------------------------------
DROP TABLE RandomInterface CASCADE;


CREATE TABLE RandomInterface
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    class TEXT,
    CONSTRAINT RandomInterface_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- CEO
-----------------------------------------------------------------------------
DROP TABLE CEO CASCADE;


CREATE TABLE CEO
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    title TEXT,
    seniority INTEGER,
    salary INTEGER NOT NULL,
    age INTEGER NOT NULL,
    name TEXT,
    intermine_end TEXT,
    fullTime BOOLEAN NOT NULL,
    employmentPeriodId INTEGER,
    addressId INTEGER,
    departmentId INTEGER,
    companyId INTEGER,
    departmentThatRejectedMeId INTEGER,
    class TEXT,
    CONSTRAINT CEO_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- ImportantPerson
-----------------------------------------------------------------------------
DROP TABLE ImportantPerson CASCADE;


CREATE TABLE ImportantPerson
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    seniority INTEGER,
    class TEXT,
    CONSTRAINT ImportantPerson_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Secretary
-----------------------------------------------------------------------------
DROP TABLE Secretary CASCADE;


CREATE TABLE Secretary
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    name TEXT,
    class TEXT,
    CONSTRAINT Secretary_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Types
-----------------------------------------------------------------------------
DROP TABLE Types CASCADE;


CREATE TABLE Types
(
    OBJECT TEXT,
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
    class TEXT,
    CONSTRAINT Types_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- Bank
-----------------------------------------------------------------------------
DROP TABLE Bank CASCADE;


CREATE TABLE Bank
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    name TEXT,
    class TEXT,
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
    OBJECT TEXT,
    id INTEGER NOT NULL,
    startDate INT8,
    endDate INT8,
    class TEXT,
    CONSTRAINT EmploymentPeriod_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- intermine_Range
-----------------------------------------------------------------------------
DROP TABLE intermine_Range CASCADE;


CREATE TABLE intermine_Range
(
    OBJECT TEXT,
    rangeEnd INTEGER NOT NULL,
    id INTEGER NOT NULL,
    rangeStart INTEGER NOT NULL,
    name TEXT,
    parentId INTEGER,
    class TEXT,
    CONSTRAINT intermine_Range_pkey UNIQUE (id)
);


-----------------------------------------------------------------------------
-- InterMineObject
-----------------------------------------------------------------------------
DROP TABLE InterMineObject CASCADE;


CREATE TABLE InterMineObject
(
    OBJECT TEXT,
    id INTEGER NOT NULL,
    class TEXT,
    CONSTRAINT InterMineObject_pkey UNIQUE (id)
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
-- Thing                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Employable                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- HasAddress                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- HasSecretarys                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Contractor                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- Manager                                                      
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
-- RandomInterface                                                      
----------------------------------------------------------------------


----------------------------------------------------------------------
-- CEO                                                      
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

