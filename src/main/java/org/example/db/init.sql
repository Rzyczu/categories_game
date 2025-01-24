

-- Tabela dla zwierząt
CREATE TABLE IF NOT EXISTS Animal (
                                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                                      name TEXT NOT NULL UNIQUE
);



-- Tabela dla jedzenia
CREATE TABLE IF NOT EXISTS Food (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                    name TEXT NOT NULL UNIQUE
);
