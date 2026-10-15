package it.buonacaccia.service;

import java.io.InputStream;

/** Archivio dei contenuti dei documenti (research R1): oggi GridFS, sostituibile senza toccare il resto. */
public interface DocumentStorage {

    /** Salva il contenuto e restituisce la chiave con cui rileggerlo. */
    String store(String filename, String contentType, byte[] content);

    InputStream open(String key);

    /** Elimina il contenuto; se non esiste non fa nulla. */
    void delete(String key);
}
