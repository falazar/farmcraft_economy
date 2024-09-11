package com.falazar.farmupcraft.database.fileformats;

public enum FileFormat {
    JSON(".json", new JsonImportStrategy(), new JsonExportStrategy()),
    NBT(".nbt", new NbtImportStrategy(), new NbtExportStrategy());

    private final String extension;
    private final ImportFormatStrategy importStrategy;
    private final ExportFormatStrategy exportStrategy;

    FileFormat(String extension, ImportFormatStrategy importStrategy, ExportFormatStrategy exportStrategy) {
        this.extension = extension;
        this.importStrategy = importStrategy;
        this.exportStrategy = exportStrategy;
    }

    public String getExtension() {
        return extension;
    }

    public ImportFormatStrategy getImportStrategy() {
        return importStrategy;
    }

    public ExportFormatStrategy getExportStrategy() {
        return exportStrategy;
    }

    public static FileFormat fromExtension(String extension) {
        for (FileFormat format : values()) {
            if (format.getExtension().equalsIgnoreCase(extension)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported file format: " + extension);
    }
}
