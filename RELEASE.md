# Release Guide

Complete guide for releasing the Spring Boot MCP Server library.

## Prerequisites

Before you can release to Maven Central, you need:

### 1. Maven Central Account

Register via the Central Portal (new process as of Jan 2024):

1. Go to **Central Portal**: https://central.sonatype.com/
2. Click **Sign In** → Sign up with GitHub account (recommended) or create account
3. After signing in, click **Add Namespace** to claim your group ID

#### Option A: GitHub-based namespace (Recommended - No verification needed!)

Use namespace: `io.github.girisenji`
- ✅ **Instant approval** - automatically verified via your GitHub account
- ✅ **No verification steps** - just add the namespace and start publishing
- ❌ Requires changing groupId in pom.xml from `com.girisenji.ai` to `io.github.girisenji`

#### Option B: Custom namespace (Requires domain ownership)

Use namespace: `com.girisenji.ai`
- ✅ Cleaner/custom groupId
- ❌ **Requires domain ownership**: You must own the domain `girisenji.ai`
- ❌ Verification process:
  1. Add namespace in Central Portal
  2. Portal generates a verification code (e.g., `CZQX28AVPBE2F92FR35K`)
  3. Add DNS TXT record to `girisenji.ai` domain:
     ```
     Host: @
     Type: TXT
     Value: CZQX28AVPBE2F92FR35K
     ```
  4. Wait for DNS propagation and verification (usually 1-24 hours)

#### Recommendation

**Use `io.github.girisenji`** - it's instantly verified and works immediately. Many popular libraries use this pattern:
- `io.github.cdimascio` (openapi-spring-webflux-validator)
- `io.github.resilience4j` (resilience4j)
- `io.github.openfeign` (feign)

**Note**: The old issues.sonatype.org JIRA system was decommissioned in Jan 2024. No email verification is needed - namespace ownership is proven via GitHub login or DNS records.

### 2. GPG Key for Signing

Generate GPG key:

```bash
# Generate key
gpg --gen-key
# Use your name: Giri Senji
# Use your email: girisenji@gmail.com

# List keys
gpg --list-secret-keys --keyid-format=long

# Export private key (for GitHub secrets)
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc

# Note: Publishing to keyservers is NOT required for Central Portal
# The new system only validates that artifacts are signed, not where keys are published
```

### 3. GitHub Repository Secrets

Add these secrets to your GitHub repository (Settings → Secrets and variables → Actions):

| Secret Name | Description | How to Get |
|-------------|-------------|------------|
| `OSSRH_USERNAME` | Sonatype JIRA username | Your JIRA account username |
| `OSSRH_TOKEN` | Sonatype JIRA token | Generate at https://s01.oss.sonatype.org/ |
| `GPG_PRIVATE_KEY` | GPG private key | Content of `private-key.asc` |
| `GPG_PASSPHRASE` | GPG key passphrase | Passphrase you set when creating GPG key |

**To get OSSRH Token (for automated deployments):**
1. Login to **Central Portal**: https://central.sonatype.com/
2. Click your account icon → **View Account**
3. Click **Generate User Token**
4. Use the username as `OSSRH_USERNAME` and password as `OSSRH_TOKEN`

**Alternative**: You can also create tokens at https://s01.oss.sonatype.org/ (legacy Nexus UI)
- Login → Your username → Profile → User Token → Access User Token

## Release Process

### Option 1: GitHub Actions (Recommended)

1. **Ensure all changes are committed and pushed to main**

```bash
git status  # Should be clean
git push origin main
```

2. **Go to GitHub Actions**

Navigate to: https://github.com/girisenji/spring-boot-mcp-server/actions

3. **Select "Release to Maven Central" workflow**

4. **Click "Run workflow"**

Fill in:
- **Release version**: `1.0.0`
- **Next development version**: `1.1.0-SNAPSHOT`

5. **Monitor the workflow**

The workflow will:
- ✅ Update version to 1.0.0
- ✅ Build and test
- ✅ Sign artifacts with GPG
- ✅ Deploy to Maven Central
- ✅ Create git tag `v1.0.0`
- ✅ Update version to 1.1.0-SNAPSHOT
- ✅ Create GitHub Release

6. **Wait for Maven Central sync**

After successful deployment:
- Artifacts appear in staging: https://s01.oss.sonatype.org/
- Auto-released to Maven Central (takes ~10 minutes)
- Synced to Maven Central (takes ~2 hours)
- Searchable at: https://search.maven.org/

### Option 2: Manual Release

If you prefer to release manually:

```bash
# 1. Set release version
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false

# 2. Commit release version
git add pom.xml
git commit -m "Release version 1.0.0"
git tag -a v1.0.0 -m "Version 1.0.0"

# 3. Deploy to Maven Central
mvn clean deploy -P release

# 4. Set next development version
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT -DgenerateBackupPoms=false

# 5. Commit next version
git add pom.xml
git commit -m "Prepare next development iteration 1.1.0-SNAPSHOT"

# 6. Push changes
git push origin main
git push origin v1.0.0
```

## Verify Release

### Check Maven Central

```bash
# Search for artifact
curl https://search.maven.org/solrsearch/select?q=g:io.github.girisenji.ai+AND+a:spring-boot-mcp-server
```

Visit: https://central.sonatype.com/artifact/io.github.girisenji.ai/spring-boot-mcp-server

### Test in Project

```xml
<dependency>
    <groupId>io.github.girisenji</groupId>
    <artifactId>spring-boot-mcp-server</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Publishing to GitHub Packages

Releases are automatically published to GitHub Packages when a GitHub Release is created.

Users can consume from GitHub Packages by adding to `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/girisenji/spring-boot-mcp-server</url>
    </repository>
</repositories>
```

And authenticating in `~/.m2/settings.xml`:

```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password>
    </server>
</servers>
```

## Troubleshooting

### GPG Signing Fails

```bash
# Verify GPG key
gpg --list-secret-keys

# Test signing
mvn clean verify -P release
```

### Deployment Fails

Check:
1. **Namespace verified**: Ensure `io.github.girisenji` namespace is verified in Central Portal (automatically verified via GitHub login)
2. **User token valid**: Generate new token at https://central.sonatype.com/
3. **GPG key published**: Key must be on public keyservers (keyserver.ubuntu.com, keys.openpgp.org)
4. **POM metadata complete**: Required fields - name, description, url, license, developers, scm
5. **GitHub secrets configured**: All 4 secrets (OSSRH_USERNAME, OSSRH_TOKEN, GPG_PRIVATE_KEY, GPG_PASSPHRASE)

### Release Not Appearing

Using Central Portal (new process):
- Login to https://central.sonatype.com/
- Go to **Deployments** to see upload status
- Auto-release is enabled by default (configured in nexus-staging-maven-plugin)

Using Legacy Nexus (if auto-release fails):
- Login to https://s01.oss.sonatype.org/
- Check **Staging Repositories**
- Close and Release manually if needed

### Common Issues

**"401 Unauthorized" during deployment:**
- User token expired or incorrect
- Generate new token at Central Portal
- Update OSSRH_USERNAME and OSSRH_TOKEN secrets

**"Namespace not verified":**
- Complete namespace verification in Central Portal
- Check verification status under "Namespaces" section

**"No public key found":**
- Publish GPG key to multiple keyservers:
  ```bash
  gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
  gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
  gpg --keyserver pgp.mit.edu --send-keys YOUR_KEY_ID
  ```

### Getting Help

- **Central Portal Support**: Email central-support@sonatype.com
- **Documentation**: https://central.sonatype.org/
- **GitHub Issues**: https://github.com/girisenji/spring-boot-mcp-server/issues
- **Central Portal Documentation**: https://central.sonatype.org/
- **Publishing Guide**: https://central.sonatype.org/publish/publish-guide/
- **Requirements**: https://central.sonatype.org/publish/requirements/
- **Central Portal**: https://central.sonatype.com/
- **Support**: Email central-support@sonatype.com (replaced old JIRA ticketing)

## heck "Staging Repositories"
- Close and Release manually if needed

## Best Practices

1. **Always test before releasing**: Run full test suite
2. **Update CHANGELOG.md**: Document all changes
3. **Update README.md**: Ensure version numbers are correct
4. **Tag releases**: Always create git tags for releases
5. **Semantic Versioning**: Follow semver (MAJOR.MINOR.PATCH)
6. **Release Notes**: Include comprehensive release notes in GitHub Release

## Post-Release Checklist

- [ ] Verify artifact on Maven Central
- [ ] Update README.md version badge
- [ ] Announce release (Twitter, blog, etc.)
- [ ] Update documentation if needed
- [ ] Test integration in sample project
- [ ] Close GitHub milestone (if using)
- [ ] Update CHANGELOG.md for next version

## Release Schedule

Suggested release cadence:

- **Patch releases** (1.0.x): Bug fixes, as needed
- **Minor releases** (1.x.0): New features, monthly/quarterly
- **Major releases** (x.0.0): Breaking changes, yearly

## Support

For release issues:
- Check Maven Central publishing guide: https://central.sonatype.org/publish/publish-guide/
- Open issue on GitHub
- Contact maintainers
