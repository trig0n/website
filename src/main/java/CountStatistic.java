public class CountStatistic {
    private String name;
    private Integer value;

    public CountStatistic() {
    }

    public CountStatistic(String name) {
        this.name = name;
        value = 0;
    }

    public CountStatistic(String name, Integer value) {
        this.name = name;
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public void increment(Integer i) {
        value += i;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}