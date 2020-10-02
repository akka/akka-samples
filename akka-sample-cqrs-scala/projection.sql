
create table if not exists "AKKA_PROJECTION_OFFSET_STORE" (
  "PROJECTION_NAME" VARCHAR(255) NOT NULL,
  "PROJECTION_KEY" VARCHAR(255) NOT NULL,
  "CURRENT_OFFSET" VARCHAR(255) NOT NULL,
  "MANIFEST" VARCHAR(4) NOT NULL,
  "MERGEABLE" BOOLEAN NOT NULL,
  "LAST_UPDATED" BIGINT NOT NULL,
  constraint "PK_PROJECTION_ID" primary key ("PROJECTION_NAME","PROJECTION_KEY")
);

create index if not exists "PROJECTION_NAME_INDEX" on "AKKA_PROJECTION_OFFSET_STORE" ("PROJECTION_NAME");

create table if not exists events (
    name varchar(256),
    event varchar(256),
    constraint pkey primary key (name, event)
);



