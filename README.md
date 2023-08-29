# irma_sms_issuer
The IRMA SMS issuer takes care of issuing a mobile phone number to [Yivi app](https://github.com/privacybydesign/irmamobile) users. It consists of a Java backend API, which connects to an [irma server (issuer)](https://github.com/privacybydesign/irmago) and an SMS gateway service, and a frontend web app.

# Running (development)
The easiest way to run the irma_sms_issuer for development purposes is by having a phone with the Yivi app installed and Docker.

### Setup listening for SMS messages:
Make sure your development machine and phone are on the same network.

To be able to get the verification code execute the following command in a separate terminal to intercept relevant local traffic:
```bash
$ while true; do { echo -e 'HTTP/1.1 200 OK\r\n'; sh test;  } | nc -l 8766; done
```

If you have an Android device you can also install [StartHere SMS Gateway App](https://m.apkpure.com/starthere-sms-gateway-app/com.bogdan.sms). This will give you a more visual experience. When the app is started, it runs a local server imitating an SMS sending gateway. SMS messages, in the form of POST requests coming from irma_sms_issuer, are sent to this messaging service and will be displayed inside the app.

### Configuration
Various configuration files, keys and settings need to be in place to be able to build and run the apps.

1. To generate the required keys, run:
```bash
$ utils/keygen.sh
```

2. Create the Java app configuration:
Copy the file `src/main/resources/config.sample.json` to `src/main/resources/config.json` and set the `sms_sender_address` to match the IP address of your localhost or the Address displayed in the StartHere SMS Gateway app. For example:

```json
{
  "sms_sender_address": "http://192.168.1.100:8766",
}
```

3. Create the webapp configuration:
Copy the file `webapp/config.example.js` to `webapp/config.js`

4. Update docker-compose.yml with your local IP address:
Set the `- "--url=http://ip-address:8088"` parameter inside `docker-compose.yml` to match the IP address of your development machine. For example:
```yml
    entrypoint:
      - "--url=http://192.168.1.105:8088"
```
Note: do not use `127.0.0.1` or `0.0.0.0` as IP addresses as this will result in the app not being able to find the issuer.

### Run
Use docker-compose to spin up the containers:
```bash
$ docker-compose up
```

By default, docker-compose caches docker images, so on a second run the previous built images will be used. A fresh build can be enforced using the --build flag.
```bash
$ docker-compose up --build
```

Navigate to `http://localhost:8080` in your browser and follow the instructions to test the complete flow.

## Manually
The java api and JavaScript frontend can be built and run manually. To do so:

### Build

To build the .war file, run:
```bash
$ gradle clean build
```

To build the webapp, run:
```bash
$ cd webapp
$ yarn install
$ build.sh en
```
The last command builds the English version of the webapp. To build another language, for example Dutch, run `build.sh nl` instead.

### Test
You can run the tests, defined in `src/test/java/foundation/privacybydesign/sms/ratelimit`, using the following command:
```bash
$ gradle test
```


### Run
Make sure you have an irma server up and running as issuer and, a valid `webapp/build/assets/config.js` and `src/main/resources/config.json` and keys. How this needs to be configured can be seen inside the Docker files. Then, run:

```bash
$ gradle appRun
```

To open the webapp navigate to `http://localhost:8080/irma_sms_issuer`. The API is accessible via `http://localhost:8080/irma_sms_issuer/api`