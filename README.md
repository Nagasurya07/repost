# Hashira Placements Assignment — Shamir Secret Sharing (Java)

This repository contains a Java solution for reconstructing the secret (f(0)) from Shamir's Secret Sharing points provided in JSON format.

Inputs are provided as JSON files with:
 `keys.n`: number of points available
`keys.k`: threshold (minimum points needed, where k = degree + 1)
- Each root as an object keyed by its x-coordinate, with `base` and `value` strings. Example:

```
"2": {
	"base": "2",
	"value": "111"
}
```

The program parses the JSON using simple regex, decodes each `value` from its given base to a BigInteger, and reconstructs the secret using exact rational Lagrange interpolation (no modular arithmetic). Negative results are possible for arbitrary integer polynomials.

## Files
- `ShamirSecretSharing.java` — main implementation and CLI
- `test1.json`, `test2.json` — sample inputs from the assignment

## How to run

From the repository root:

```bash
javac ShamirSecretSharing.java
java ShamirSecretSharing               # runs default test1.json and test2.json
java ShamirSecretSharing test1.json    # run a specific file
java ShamirSecretSharing test2.json    # run another specific file

# Optional: finite-field mode (classic Shamir) with explicit prime p
java ShamirSecretSharing --mod --prime 167653306246241
```

## Current outputs

With the current repository content:
- `test1.json` → `3`
- `test2.json` → `-6290016743746469796`

If you want the classic secret over a finite field (mod p) and the assignment’s stated answer for test2:

```bash
java ShamirSecretSharing --mod --prime 167653306246241
```

Outputs:
- `test1.json` → `3` (mod p)
- `test2.json` → `42` (mod 167653306246241)

## Notes
- If you need classic Shamir behavior over a finite field, we can add modular arithmetic with a chosen prime and compute all operations mod p.
- The JSON is parsed with regex for simplicity since the format is constrained; using a JSON library would be more robust for arbitrary input.

## Repository link

https://github.com/Nagasurya07/repost
