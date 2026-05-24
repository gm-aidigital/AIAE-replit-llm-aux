{ pkgs }: {
  deps = [
    # Backend toolchain — Java 21 LTS.
    #
    # Why Java 21 and not Java 25 (the company's preferred LTS)?
    # Replit's current pinned nixpkgs channel (stable-24_11) ships JDK up to
    # version 23. JDK 25 GA only landed in Sept 2025 and is not yet in the
    # channel Replit supports. Using `pkgs.jdk25` produces:
    #   error: attribute 'jdk25' missing
    #
    # When Replit bumps to a channel that includes jdk25 (likely stable-25_11
    # or later), switch this to `pkgs.jdk25` and bump
    # `<java.version>` / `<maven.compiler.release>` in the scaffold's parent
    # pom.xml. Until then, Java 21 LTS is the canonical Replit baseline.
    pkgs.jdk21
    pkgs.maven

    # Frontend toolchain
    pkgs.nodejs_22

    # Postgres CLI client (psql) — useful for ad-hoc queries from the Replit
    # shell. The managed SQL Database itself is provided by the postgresql-16
    # module declared in .replit, not by this Nix package.
    pkgs.postgresql_16

    # Shell utilities the Agent uses while scaffolding.
    pkgs.git
    pkgs.curl
    pkgs.jq
    pkgs.yq-go
  ];
}
