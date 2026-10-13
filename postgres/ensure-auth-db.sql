SELECT 'CREATE DATABASE "cg-auth"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cg-auth')
\gexec
