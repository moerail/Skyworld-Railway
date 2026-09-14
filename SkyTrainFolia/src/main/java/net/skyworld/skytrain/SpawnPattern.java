package net.skyworld.skytrain;

import java.util.*;
import java.util.random.RandomGenerator;

/** Bounded TC-style composition. Template contents are resolved separately by STF. */
final class SpawnPattern {
    enum Center { NONE, LEFT, RIGHT, MIDDLE }
    record Part(String name, int count, double weight, List<Part> children) {}
    record Parsed(List<Part> parts, Center center) {
        List<String> expand(RandomGenerator random, int limit) {
            List<String> result = new ArrayList<>();
            expandParts(parts, random, limit, result);
            return List.copyOf(result);
        }
    }
    private final String text;
    private final List<String> names;
    private int index;
    private Center center = Center.NONE;
    private SpawnPattern(String text, Collection<String> names) {
        if (text.length() > 4096) throw new IllegalArgumentException("Spawn pattern too long");
        this.text = text;
        this.names = names.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }
    static Parsed parse(String text, Collection<String> names) {
        SpawnPattern parser = new SpawnPattern(text, names);
        var parts = parser.sequence(0);
        if (parts.isEmpty()) throw new IllegalArgumentException("Empty spawn pattern");
        return new Parsed(List.copyOf(parts), parser.center);
    }
    private List<Part> sequence(int depth) {
        if (depth > 16) throw new IllegalArgumentException("Spawn nesting exceeds 16");
        List<Part> result = new ArrayList<>();
        while (index < text.length()) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c)) { index++; continue; }
            if ("]>)}".indexOf(c) >= 0) {
                index++;
                if (depth > 0) return result;
                center = Center.LEFT;
                continue;
            }
            int prefix = index;
            while (index < text.length() && (Character.isDigit(text.charAt(index)) || text.charAt(index)=='.')) index++;
            String quantity = text.substring(prefix, index);
            double weight = -1;
            if (index < text.length() && text.charAt(index)=='%') {
                weight = Double.parseDouble(quantity);
                if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid spawn weight");
                prefix = ++index;
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
                quantity = text.substring(prefix, index);
            }
            int count = quantity.isEmpty() ? 1 : Integer.parseInt(quantity);
            if (count < 0 || count > 1024 || index >= text.length()) throw new IllegalArgumentException("Invalid spawn count");
            c = text.charAt(index);
            if ("[<({".indexOf(c)>=0) {
                index++;
                List<Part> children = sequence(depth+1);
                if (depth==0 && result.isEmpty() && index==text.length() && quantity.isEmpty() && weight<0) {
                    center = "]>)}".indexOf(text.charAt(text.length()-1))>=0 ? Center.MIDDLE : Center.RIGHT;
                    result.addAll(children);
                } else result.add(new Part(null,count,weight,List.copyOf(children)));
                continue;
            }
            String name = null;
            for (String candidate : names) if (text.startsWith(candidate,index)
                    && (candidate.length()>1 || "mspht".indexOf(c)<0)) { name=candidate; break; }
            if (name==null && "mspht".indexOf(c)>=0) name=String.valueOf(c);
            if (name==null) throw new IllegalArgumentException("Unknown spawn pattern at: " + text.substring(index));
            index+=name.length();
            result.add(new Part(name,count,weight,List.of()));
        }
        return result;
    }
    private static void expandParts(List<Part> parts, RandomGenerator random, int limit, List<String> out) {
        double total = parts.stream().filter(p->p.weight>=0).mapToDouble(Part::weight).sum();
        if (!Double.isFinite(total)) throw new IllegalArgumentException("Spawn weight overflow");
        double chosen = total>0 ? random.nextDouble(total) : -1;
        Part selected = null;
        for (Part p: parts) if(p.weight>=0 && selected==null && chosen>=0) {
            chosen-=p.weight;
            if(chosen<0) selected=p;
        }
        for(Part p: parts) if(p.weight<0 || p==selected) for(int i=0;i<p.count;i++) {
            if(p.name==null) expandParts(p.children,random,limit,out);
            else { if(out.size()>=limit) throw new IllegalArgumentException("Spawn exceeds cart limit " + limit); out.add(p.name); }
        }
    }
}
