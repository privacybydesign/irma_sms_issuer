# irma_sms_issuer
The IRMA SMS issuer takes care of issuing a mobile phone number to [Yivi app](https://github.com/privacybydesign/irmamobile) users. It consists of a Java backend API, which connects to an [irma server (issuer)](https://github.com/privacybydesign/irmago) and an SMS gateway service, and a frontend web app.

# Running (development)
The easiest way to run the irma_sms_issuer for development purposes is by having an Android phone with the Yivi app installed and Docker.

### Set up local SMS messaging gateway (Android only):
Make sure your development machine and phone are on the same network. Install [StartHere SMS Gateway App](https://m.apkpure.com/starthere-sms-gateway-app/com.bogdan.sms) on your Android device. When the app is started, it runs a local server imitating an SMS sending gateway. SMS messages, in the form of POST requests coming from irma_sms_issuer, are sent to this messaging service and will be displayed inside the app. 

### Configuration
Various configuration files, keys and settings need to be in place to be able to build and run the apps.

1. To generate the required keys, run:
```bash
$ utils/keygen.sh
```


2. Update the config.sample.json configuration:
Set the `sms_sender_address` inside `src/main/resources/config.sample.json` to match the Address displayed in the StartHere SMS Gateway app. For example:

```json
{
  ...
  "sms_sender_address": "http://192.168.1.100:8766",
  ...
}
```

3. Update docker-compose with your local IP address:
Set the `- "--url=http://ip-address:8088"` parameter inside `docker-compose.yml` to match the IP address of your development machine. For example:
```yml
{
  ...
    entrypoint:
      - "--url=http://192.168.1.105:8088" 
  ...
}
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

### Open browser
To test the complete flow, you need to use a browser which runs in insecure mode. This is required to bypass CORS issues. The example below launches Google Chrome browser on a Mac at `http://localhost:8080` with web-security disabled:
```bash
$ open -n -a Google\ Chrome --args --user-data-dir=/tmp/temp_chrome_user_data_dir http://localhost:8080/ --disable-web-security
```

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
```bash
$ gradle appRun
```