(:JIQS: ShouldRun; Output="({ "foo" : "foo", "bar" : "foobar", "int" : 1 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 1 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "def", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 42 }, { "foo" : "foo", "bar" : "foobar", "int" : 1, "date" : "2021-01-01" }, { "foo" : "foo", "date" : "2020-12-31", "bar" : "def" }, { "foo" : "foo", "bar" : "foobar", "date" : "2021-01-01" }, { "foo" : "foo", "bar" : "def", "date" : "2021-01-01" }, { "foo" : "foo", "bar" : "foobar", "int" : 42, "date" : "2021-01-01" }, { "foo" : "foo", "bar" : "def", "date" : "2021-01-01" }, { "foo" : "foo", "bar" : "def", "date" : "2021-01-01" }, { "foo" : "foo", "bar" : "foobar", "date" : "2021-01-01" }, { "foo" : "foo", "bar" : "def", "date" : "2021-01-01" }, { "foo" : "foo", "bar" : "foobar", "date" : "2021-01-01" })" :)
declare type local:a as { "int" : "integer=43" };

exactly-one(validate type local:a* { { } })

