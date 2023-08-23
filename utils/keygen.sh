#!/bin/bash

dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

SK=$dir/../build/resources/main/sk
PK=$dir/../build/resources/main/pk

# Create dir if not exists
mkdir -p $dir/../build/resources/main


if [ ! -e "$SK" ]; then
    # Generate a private key in PEM format
    openssl genrsa -out ${SK}.pem 2048
    # Convert it to DER for Java
    openssl pkcs8 -topk8 -inform PEM -outform DER -in ${SK}.pem -out ${SK}.der -nocrypt
    # Calculate corresponding public key, saved in PEM format
    openssl rsa -in ${SK}.pem -pubout -outform PEM -out ${PK}.pem
    # Calculate corresponding public key, saved in DER format
    openssl rsa -in ${SK}.pem -pubout -outform DER -out ${PK}.der
fi