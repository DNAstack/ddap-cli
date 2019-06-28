# DDAP CLI

## Overview

The DDAP CLI is a shell utility for obtaining access tokens from the Data Discovery and Access Platform (DDAP)
from a headless environment (such as a cloud VM).

## Usage

### Login
The command-line tool login command allows you to do a web-based browser login on a separate machine, to authenticate
inside a cloud VM.

Start by running login command.
```bash
ddap-cli login -l https://ddap-frontend.prod.dnastack.com -u ga4gh hinxton
```
You'll see the following output:
```
Visit this link in a web browser to login: https://ic-prod-dot-hcls-data-connect-demo.appspot.com/identity/v1alpha/dnastack/authorize?response_type=code&clientId=d0e75142-ee26-4217-86dd-0b01c9f8f811&redirect_uri=https://ddap-frontend.prod.dnastack.com/api/v1alpha/dnastack/cli/login/3e9954cc-138b-44ee-b4ca-17e7430d9405&state=eyJhbGciOiJIUzUxMiJ9.eyJhdWQiOiJodHRwczovL2RkYXAtZnJvbnRlbmQucHJvZC5kbmFzdGFjay5jb20iLCJleHAiOjE1NjE3NDY4MjQsInRva2VuS2luZCI6InN0YXRlIiwicHVycG9zZSI6IkNMSV9MT0dJTiIsImNsaVNlc3Npb25JZCI6IjNlOTk1NGNjLTEzOGItNDRlZS1iNGNhLTE3ZTc0MzBkOTQwNSJ9.6HvXvc1SKEOAG51TSCiU7O31EYmd8Fchf2N-2VXQ5aAFkAsYdRFpjQcFEEu_btiiuPqYTTtiguZWO9kZnMQIMA&scope=openid%20ga4gh%20account_admin%20identities&login_hint=
Waiting for web login to complete for next 600 seconds...
```

You'll need to visit that link in a web-browser (possibly on another computer). Afterwards, you'll see this:
```
Login successful
Login context saved
```

Now you're ready to try running other commands!

Your login context is saved in a file in your home directory (`$HOME/.ddap-cli`),
and will be used to perform subsequent commands.

### List

_Note: You must be logged in to use this command._

The list command shows you all the available resources:
```bash
ddap-cli list
```

Example output:
```yaml
resources:
  sample-resource:
    views:
      discovery-access:
        ui:
          label: "Beacon Discovery Access"
        interfaces:
          http:beacon:
            uri:
            - "https://gatekeeper-cafe-variome.staging.dnastack.com/beacon/query"
      file-access:
        ui:
          label: "Full File Read Access"
        interfaces:
          gcp:gs:
            uri:
            - "gs://sample-resource-controlled-access"
          http:gcp:gs:
            uri:
            - "https://www.googleapis.com/storage/v1/b/sample-resource-controlled-access"
    ui:
      access: "registered, controlled"
      description: "Demo Controlled Access sample dataset.\
        \ See: https://www.nature.com/articles/nature15393."
      label: "Sample Resource"
      size: "250 GB"
      tags: "Demo, Genomics, Research"
      year: "2017"
```

### Get Access

_Note: You must be logged in to use this command._

The `get-access` command can request and download access tokens for resources protected by the DAM.

At minimum, you must specify a resource and view that you are trying to obtain.

```bash
ddap-cli get-access -r sample-resource file-access
```

This defaults to printing the token response to the console:
```yaml
name: "sample-resource"
view:
  ui:
    label: "Full File Read Access"
  interfaces:
    gcp:gs:
      uri:
      - "gs://sample-resource-controlled-access"
    http:gcp:gs:
      uri:
      - "https://www.googleapis.com/storage/v1/b/sample-resource-controlled-access"
account: "ica664fdf50e3da4f7681496f3950c@hcls-data-connect-demo.iam.gserviceaccount.com"
token: "d4V9eqbCxVD9oiAYy2icK1hTgewSuGhZuVZO73AzyvnZv8L24kMSQtJS49vtfFPK5OVlMi4YWFFyxIQKYwtaEXsQ3Qudxih/cmZc9TRkldhu+LvsMDbk7LI5bw335h5z/w54V2bOJpO0jG0PohEd6AqbkBuYz7VIuO3Woq4CGON4AzGeOXqECyyEyQJRzvSMglbnc35Y7NiqoIKGv9mfeA=="
ttl: "1h"
```

You can also write the token to an environment file for loading into a script.

```bash
ddap-cli get-access -r sample-resource file-access -f $(pwd)/setup.env
```

Which outputs:

```
Access token acquired
Output written to setup.env
Use `source` to load into environment
Example:

source setup.env
curl ${HTTP_BUCKET_URL}/o?access_token=${TOKEN}
```

For a GCS bucket, the `setup.env` file will look like this:
```bash
export TOKEN=d4V9eqbCxVD9oiAYy2icK1hTgewSuGhZuVZO73AzyvnZv8L24kMSQtJS49vtfFPK5OVlMi4YWFFyxIQKYwtaEXsQ3Qudxih/cmZc9TRkldhu+LvsMDbk7LI5bw335h5z/w54V2bOJpO0jG0PohEd6AqbkBuYz7VIuO3Woq4CGON4AzGeOXqECyyEyQJRzvSMglbnc35Y7NiqoIKGv9mfeA==
export HTTP_BUCKET_URL=https://www.googleapis.com/storage/v1/b/sample-resource-controlled-access
```
