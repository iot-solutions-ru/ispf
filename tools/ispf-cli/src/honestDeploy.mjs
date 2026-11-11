/** Fail closed when the API returns HTTP 200 but an honest deploy status is not OK. */
export function assertHonestDeployStatus(json) {
  const deployStatus = json?.status;
  if (deployStatus != null && deployStatus !== "OK") {
    const err = new Error(`deploy ${deployStatus}`);
    err.details = json;
    throw err;
  }
}
