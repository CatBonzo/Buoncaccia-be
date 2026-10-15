package it.buonacaccia.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

/** Implementazione su GridFS: nessun contenitore in più rispetto a MongoDB. */
@Component
@RequiredArgsConstructor
public class GridFsDocumentStorage implements DocumentStorage {

    private final GridFsTemplate gridFs;

    @Override
    public String store(String filename, String contentType, byte[] content) {
        return gridFs.store(new ByteArrayInputStream(content), filename, contentType).toHexString();
    }

    @Override
    public InputStream open(String key) {
        try {
            var file = gridFs.findOne(new Query(Criteria.where("_id").is(new ObjectId(key))));
            return gridFs.getResource(file).getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        gridFs.delete(new Query(Criteria.where("_id").is(new ObjectId(key))));
    }
}
