import java.util.ArrayList;
import java.util.List;

public class Host {
    String ip;
    List<CountStatistic> counts;

    Host() {
        counts = new ArrayList<>();
    }

    Host(String ip, List<CountStatistic> counts) {
        this.ip = ip;
        this.counts = counts;
    }

    void incrementCount(String name) {
        Boolean found = false;
        for (CountStatistic s : counts) {
            if (s.name.equals(name)) {
                s.value++;
                found = true;
            }
        }
        if (!found) counts.add(new CountStatistic(name, 1));
    }
}

