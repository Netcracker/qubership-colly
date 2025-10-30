import {Namespace} from "./namespaces";

export type Environment = {
    id: number;
    name: string;
    namespaces: Namespace[];
    cluster: { name: string };
    owners: string[];
    teams: string[];
    status: EnvironmentStatus;
    type: EnvironmentType;
    labels: string[];
    description: string;
    deploymentVersion: string;
    monitoringData: Record<string, number>;
    expirationDate?: Date;
    cleanInstallationDate: Date;
};
export type EnvironmentStatus = "IN_USE" | "RESERVED" | "FREE" | "MIGRATING";

export const ALL_STATUSES: EnvironmentStatus[] = ["FREE", "IN_USE", "MIGRATING", "RESERVED"]

export const STATUS_MAPPING = {
    IN_USE: "In Use",
    FREE: "Free",
    MIGRATING: "Migrating",
    RESERVED: "Reserved"
};

export type EnvironmentType =
    "ENVIRONMENT"
    | "PORTAL"
    | "CSE_TOOLSET"
    | "DESIGN_TIME"
    | "APP_DEPLOYER"
    | "INFRASTRUCTURE"
    | "UNDEFINED"

export const ALL_TYPES: EnvironmentType[] = ["ENVIRONMENT", "PORTAL", "CSE_TOOLSET", "DESIGN_TIME", "APP_DEPLOYER", "INFRASTRUCTURE", "UNDEFINED"];
export const ENVIRONMENT_TYPES_MAPPING = {
    ENVIRONMENT: "Environment",
    PORTAL: "Portal",
    CSE_TOOLSET: "CSE Toolset",
    DESIGN_TIME: "Design Time",
    APP_DEPLOYER: "App Deployer",
    INFRASTRUCTURE: "Infrastructure",
    UNDEFINED: "Undefined"
}

