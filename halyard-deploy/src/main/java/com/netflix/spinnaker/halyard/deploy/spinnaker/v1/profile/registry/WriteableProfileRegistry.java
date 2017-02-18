package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.StorageObject;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
public class WriteableProfileRegistry {
  private Storage storage;
  private WriteableProfileRegistryProperties properties;

  @Autowired
  String spinconfigBucket;

  WriteableProfileRegistry(WriteableProfileRegistryProperties properties) {
    try {
      HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      GoogleCredential credential = loadCredential(httpTransport, jsonFactory, properties.getJsonPath());
      this.storage = new Storage.Builder(httpTransport, jsonFactory, credential)
          .setApplicationName("halyard")
          .build();
      this.properties = properties;
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Failed to set up configured writeable profile registry", e);
    }
  }

  private GoogleCredential loadCredential(HttpTransport transport, JsonFactory factory, String jsonPath) throws IOException {
    GoogleCredential credential;
    if (!jsonPath.isEmpty()) {
      FileInputStream stream = new FileInputStream(jsonPath);
      credential = GoogleCredential.fromStream(stream, transport, factory)
          .createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL));
      log.info("Loaded credentials from " + jsonPath);
    } else {
      log.info("Using default application credentials.");
      credential = GoogleCredential.getApplicationDefault();
    }
    return credential;
  }

  public void writeArtifactConfig(BillOfMaterials bom, SpinnakerArtifact artifact, String profileName, String contents) {
    String version = bom.getServices().getArtifactVersion(artifact);
    String name = ProfileRegistry.profilePath(artifact, version, profileName);
    try {
      byte[] bytes = contents.getBytes();
      StorageObject object = new StorageObject()
          .setBucket(spinconfigBucket)
          .setName(name);

      ByteArrayContent content = new ByteArrayContent("text", bytes);
      storage.objects().insert(spinconfigBucket, object, content).execute();
    } catch (IOException e) {
      log.error("Failed to write new object " + name, e);
      throw new HalException(new ConfigProblemBuilder(Severity.FATAL, "Failed to write to " + name).build());
    }
  }
}
