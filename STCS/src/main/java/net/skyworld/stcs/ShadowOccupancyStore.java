package net.skyworld.stcs;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.skyworld.sta.api.v3.ConsistObservation.Member;

/** Durable last-known shadow evidence, independent of the M1 historical ledger. */
final class ShadowOccupancyStore {
    record TrainRecord(String name, Set<String> resources, long graphRevision, UUID sourceSession,
            List<Member> positions, boolean outsideConfirmed) {
        TrainRecord {
            Objects.requireNonNull(name);
            resources=Set.copyOf(resources);positions=List.copyOf(positions);
            if(graphRevision < -1 || resources.stream().anyMatch(String::isBlank)
                    || positions.stream().map(Member::id).distinct().count()!=positions.size()
                    || (outsideConfirmed && !resources.isEmpty()))
                throw new IllegalArgumentException("Invalid shadow train record");
        }
        static TrainRecord legacy(Set<String> resources) {
            return new TrainRecord("--",resources,-1,null,List.of(),false);
        }
        TrainRecord withResources(Set<String> value) {
            return new TrainRecord(name,value,graphRevision,sourceSession,positions,outsideConfirmed && value.isEmpty());
        }
    }
    record Document(int version, Map<UUID,TrainRecord> trains) {
        Document {
            if(version!=2) throw new IllegalArgumentException("Unsupported shadow occupancy version");
            trains=Map.copyOf(trains);
        }
    }
    static Map<UUID,TrainRecord> load(Path file) throws IOException {
        if(!Files.exists(file)) return Map.of();
        try {
            var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var gson=new Gson();
            if(root.has("version")) {
                var version=root.getAsJsonPrimitive("version");
                if(!version.isNumber() || !version.getAsString().equals("2"))
                    throw new IllegalArgumentException("Unsupported shadow occupancy version");
                var doc=gson.fromJson(root,Document.class);
                return doc.trains();
            }
            Map<String,Set<String>> legacy=gson.fromJson(root,new TypeToken<Map<String,Set<String>>>(){}.getType());
            Map<UUID,TrainRecord> result=new LinkedHashMap<>();
            legacy.forEach((id,resources)->result.put(UUID.fromString(id),TrainRecord.legacy(resources)));
            Path backup=file.resolveSibling(file.getFileName()+".v1.bak");
            try { Files.copy(file,backup); } catch(FileAlreadyExistsException alreadyBackedUp) { }
            return result;
        } catch(RuntimeException ex) {
            throw new IOException("Invalid shadow occupancy; original file retained: "+file,ex);
        }
    }
    static String encode(Map<UUID,TrainRecord> trains) {
        return new Gson().toJson(new Document(2,trains));
    }
    static void save(Path file,String json) throws IOException {
        Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(tmp,json);
        try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
}
