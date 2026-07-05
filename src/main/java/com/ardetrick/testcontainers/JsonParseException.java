package com.ardetrick.testcontainers;

/** Thrown when a JSON response cannot be parsed. */
class JsonParseException extends RuntimeException {

  JsonParseException(String message) {
    super(message);
  }
}
