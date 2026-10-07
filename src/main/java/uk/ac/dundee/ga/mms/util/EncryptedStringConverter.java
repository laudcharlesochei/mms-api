package uk.ac.dundee.ga.mms.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** JPA converter that transparently encrypts notes columns with AES-256-GCM. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return NotesCipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return NotesCipher.decrypt(dbData);
    }
}
