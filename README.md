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

Afterwards, you'll see this:
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

_Note: You must be logged in to use this command. You must authorize for resource each time you request access._

The `get-access` command can request and download access tokens for resources protected by the DAM.

At minimum, you must specify a resource and view that you are trying to obtain.

```bash
ddap-cli get-access -r sample-resource -v file-access
```

You'll see the following output:
```
Visit this link in a web browser to authorize for resource [beacon-resource/beacon-resource-view/discovery] : http://localhost:8085/api/v1alpha/realm/milan-new-token-flow/resources/authorize?resource=1;beacon-resource/views/beacon-resource-view/roles/discovery&redirectUri=http://localhost:8085/api/v1alpha/realm/milan-new-token-flow/cli/0480590d-7e0a-46fb-8f35-18af22d73f56/authorize/callback?resource=1;beacon-resource/views/beacon-resource-view/roles/discovery
Waiting for web authorization to complete for next 600 seconds...
```

Once authorized you will see token response printed to the console:
```yaml
resources:
  ? https://data-access-manager.hcls.com/dam/my-realm/resources/beacon-resource/views/beacon-resource-view/roles/discovery
  : interfaces:
      http:beacon:
        uri:
        - "https://beacon-address.com/beacon/query"
    access: "0"
    permissions:
    - "metadata"
access:
  0:
    account: "gc_account_1111"
    access_token: "d4V9eqbCxVD9oiAYy2icK1hTgewSuGhZuVZO73AzyvnZv8L24kMSQtJS49vtfFPK5OVlMi4YWFFyxIQKYwtaEXsQ3Qudxih/cmZc9TRkldhu+LvsMDbk7LI5bw335h5z/w54V2bOJpO0jG0PohEd6AqbkBuYz7VIuO3Woq4CGON4AzGeOXqECyyEyQJRzvSMglbnc35Y7NiqoIKGv9mfeA=="
```

You can also write the token to an environment file for loading into a script.

```bash
ddap-cli get-access -r sample-resource -v file-access -f $(pwd)/setup.env
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
