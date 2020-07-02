package sjtu.sdic.kvstore.core;

import lombok.extern.slf4j.Slf4j;
import sjtu.sdic.kvstore.common.Utils;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;

@Slf4j
public class Chord {
    private final ConcurrentSkipListSet<VirtualGroup> ring;

    /**
     * ring - A sorted set which stores VirtualGroups. The order is corresponding to index order
     */
    public Chord() {
        ring = new ConcurrentSkipListSet<>(Comparator.comparingInt(v -> v.index));
    }


    public void addGroup(VirtualGroup vg){
        if (ring.contains(vg)) {
            log.warn("Hash ring group corrupted, {}", vg.name);
        }
        ring.add(vg);
    }

    public boolean removeGroup(VirtualGroup vg) {
        log.info("Remove Virtual Group {} from hash ring", vg.name);
        return ring.remove(vg);
    }

    /**
     *
     * @param key - A string identifier
     * @return Group - which the key belongs to, null if no group in the chord
     */
    public Group mapKey(String key) {
        int index;
        VirtualGroup vg;

        index = Utils.hash(key);
        vg = ring.ceiling(new VirtualGroup(index, null, null));
        if(vg == null) {
            if (ring.size() == 0) {
                log.warn("Hash ring size is zero, mapping key [{}] action aborted", key);
                return null;
            }
            // should return the first vn
            vg = ring.first();
            assert vg.index < index;
            assert ring.last().index < index;
        }

        return vg.group;
    }

    public VirtualGroup getPrevGroup(VirtualGroup vg) {
        VirtualGroup res;
        res = ring.floor(new VirtualGroup(vg.index - 1, "", null));
        if (res == null)
            return ring.last();
        return res;
    }

    public VirtualGroup getNextGroup(VirtualGroup vg) {
        if (vg.index == Integer.MAX_VALUE)
            return ring.first();
        VirtualGroup res;
        res = ring.ceiling(new VirtualGroup(vg.index + 1, "", null));
        if (res == null)
            return ring.first();
        return res;
    }
}
