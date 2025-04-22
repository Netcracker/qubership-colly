export type Environment = {
    id: number;
    name: string;
    namespaces: { name: string }[];
    cluster: { name: string };
    owner: string;
    status: EnvironmentStatus;
    description: string;
};
export type EnvironmentStatus = "IN_USE" | "RESERVED" | "FREE" | "MIGRATING";

export const ALL_STATUSES: EnvironmentStatus[] = ["FREE", "IN_USE", "MIGRATING", "RESERVED"]
