# Security Policy

## Project status

VantaLoader is pre-alpha scaffolding. It is not yet a functioning Minecraft mod loader and has no supported release versions. This policy does not imply a security response-time commitment.

## Reporting a vulnerability

**Do not report suspected vulnerabilities in public issues, discussions, or pull requests.**

Use the repository's GitHub **Security** tab and select **Report a vulnerability** to submit a private report. Include the affected component and revision, impact, reproduction steps or a minimal proof of concept, and relevant logs with personal information, tokens, and private server details removed.

If private vulnerability reporting is unavailable, contact the repository owner through their GitHub profile and ask for a private reporting channel. Do not publish exploit details while trying to establish contact.

The maintainer will review reports as practical and may request additional information. Please allow time for triage and coordinated remediation before public disclosure.

## Scope

The intended scope includes the loader bootstrap, metadata parser, dependency resolver, transformation pipeline, Mixin integration, reload coordinator, and distribution tooling. Include exact versions when reporting third-party dependency issues.

## Important security boundary

**VantaLoader is not a sandbox for untrusted Java mods.** Code loaded into Minecraft generally has the permissions of the operating-system user running the game. Mod metadata, declared capabilities, and loader APIs must not be treated as a security boundary.

Only install mods from sources you trust. Do not rely on VantaLoader to make malicious or untrusted Java code safe.
