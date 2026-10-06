package org.thinkingstudio.constancy.compat.ctm;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Builds a required pack containing external OptiFine CTM rules and Atlas textures. */
public final class GeneratedLegacyPack {
    private GeneratedLegacyPack() {}

    public static void prepare(Path root, Path resourcepacks) {
        try {
            if (Files.exists(root)) Files.walk(root).sorted(java.util.Comparator.reverseOrder()).filter(p -> !p.equals(root)).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            Files.createDirectories(root);
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Constancy legacy CTM atlas\"}}", StandardCharsets.UTF_8);
            if (Files.isDirectory(resourcepacks)) try (var stream = Files.list(resourcepacks)) { stream.forEach(p -> { if (Files.isDirectory(p)) collectDirectory(root, p); else if (p.toString().toLowerCase().endsWith(".zip")) collectZip(root, p); }); }
            Path atlas = root.resolve("assets/minecraft/atlases/blocks.json");
            Path generatedTextures = root.resolve("assets/minecraft/textures/ctm");
            boolean hasTextures = Files.exists(generatedTextures) && Files.walk(generatedTextures).anyMatch(p -> p.toString().endsWith(".png"));
            if (hasTextures) { Files.createDirectories(atlas.getParent()); Files.writeString(atlas, "{\"sources\":[{\"type\":\"directory\",\"source\":\"block\",\"prefix\":\"block/\"},{\"type\":\"directory\",\"source\":\"ctm\",\"prefix\":\"ctm/\"}]}", StandardCharsets.UTF_8); }
            else Files.deleteIfExists(atlas);
        } catch (Exception ignored) {}
    }

    private static void collectDirectory(Path root, Path pack) {
        try (var stream = Files.walk(pack)) { stream.filter(p -> { String s=p.toString().replace('\\','/'); return s.contains("/assets/") && s.contains("/optifine/ctm/") && (s.endsWith(".png") || s.endsWith(".properties")); }).forEach(p -> { try {
            String s=p.toString().replace('\\','/'); int a=s.indexOf("/assets/")+8; String rel=s.substring(a); int slash=rel.indexOf('/'); String ns=rel.substring(0,slash); String tail=rel.substring(rel.indexOf("/optifine/ctm/")+15); if (tail.endsWith(".png")) { String key=ns+"/"+tail.substring(0,tail.length()-4); Path out=root.resolve("assets/minecraft/textures/ctm/"+key+".png"); Files.createDirectories(out.getParent()); Files.copy(p,out,java.nio.file.StandardCopyOption.REPLACE_EXISTING); writeModel(root,key); } else copyRule(root, rel, p); } catch (Exception ignored) {} }); } catch (Exception ignored) {}
    }

    private static void collectZip(Path root, Path pack) {
        try (ZipFile zip=new ZipFile(pack.toFile())) { var e=zip.entries(); while(e.hasMoreElements()) { ZipEntry z=e.nextElement(); String n=z.getName(); if(z.isDirectory() || !n.startsWith("assets/") || !n.contains("/optifine/ctm/") || !(n.endsWith(".png") || n.endsWith(".properties"))) continue; String rel=n.substring(7); int slash=rel.indexOf('/'); String ns=rel.substring(0,slash); String tail=rel.substring(rel.indexOf("/optifine/ctm/")+15); if(tail.endsWith(".png")) { String key=ns+"/"+tail.substring(0,tail.length()-4); Path out=root.resolve("assets/minecraft/textures/ctm/"+key+".png"); Files.createDirectories(out.getParent()); try(InputStream in=zip.getInputStream(z)) { Files.copy(in,out,java.nio.file.StandardCopyOption.REPLACE_EXISTING); } writeModel(root,key); } else { Path out=root.resolve("assets/"+rel); Files.createDirectories(out.getParent()); try(InputStream in=zip.getInputStream(z)) { Files.copy(in,out,java.nio.file.StandardCopyOption.REPLACE_EXISTING); } } } } catch (Exception ignored) {}
    }

    private static void copyRule(Path root, String rel, Path source) throws Exception { Path out=root.resolve("assets/"+rel); Files.createDirectories(out.getParent()); Files.copy(source,out,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
    private static void writeModel(Path root, String key) throws Exception { String id=Integer.toHexString(key.hashCode()); Path model=root.resolve("assets/continuity/models/legacy_tiles/generated_"+id+".json"); Files.createDirectories(model.getParent()); Files.writeString(model,"{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:ctm/"+key+"\"}}",StandardCharsets.UTF_8); }
}
