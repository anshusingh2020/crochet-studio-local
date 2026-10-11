export async function readProfileResponse(response: Response): Promise<any> {
  if (response.status === 401 || response.redirected) {
    throw new Error("Your session has expired. Please sign in again.");
  }
  const fallback = "The studio could not complete your request. Please try again.";
  let data: any;
  try {
    data = await response.json();
  } catch {
    throw new Error(fallback);
  }
  if (!data || typeof data !== "object" || Array.isArray(data)) throw new Error(fallback);
  if (!response.ok) throw new Error(typeof data.error === "string" ? data.error : fallback);
  return data;
}
