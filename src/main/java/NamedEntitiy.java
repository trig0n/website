class NamedEntitiy {
    String name;

    NamedEntitiy() {
    }

    NamedEntitiy(String name) {
        this.name = name;
    }
}

class CountStatistic extends NamedEntitiy {
    Integer value;

    CountStatistic(String name) {
        super(name);
        value = 0;
    }

    CountStatistic(String name, Integer value) {
        super(name);
        this.value = value;
    }
}

class LongStatistic extends NamedEntitiy {
    Long value;

    LongStatistic(String name) {
        super(name);
        value = new Long("0");
    }

    LongStatistic(String name, Long value) {
        super(name);
        this.value = value;
    }
}