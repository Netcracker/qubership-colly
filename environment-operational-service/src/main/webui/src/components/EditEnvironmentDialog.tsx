import {
    Autocomplete,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    TextField
} from "@mui/material";
import React, {useEffect} from "react";
import {
    ALL_STATUSES,
    ALL_TYPES,
    Environment,
    ENVIRONMENT_TYPES_MAPPING,
    EnvironmentStatus,
    EnvironmentType,
    STATUS_MAPPING
} from "../entities/environments";
import {AdapterDayjs} from '@mui/x-date-pickers/AdapterDayjs';
import {LocalizationProvider} from '@mui/x-date-pickers/LocalizationProvider';
import {DatePicker} from "@mui/x-date-pickers";
import dayjs from "dayjs";

type Props = {
    show: boolean
    environment: Environment;
    allLabels: string[];
    onClose: () => void;
    onSave: (env: Environment) => void;
};

export default function EditEnvironmentDialog({show, environment, allLabels, onClose, onSave}: Props) {

    const [localEnv, setLocalEnv] = React.useState<Environment>(environment);

    useEffect(() => {
        setLocalEnv(environment);
    }, [environment]);

    const handleSubmit = () => {
        onSave(localEnv);
    };

    return <Dialog open={show} onClose={onClose} fullWidth={true} maxWidth="sm">
        <DialogTitle>Edit Environment</DialogTitle>
        <DialogContent>
            <TextField
                label="Name"
                value={localEnv.name || ''}
                disabled
                fullWidth
                margin="dense"/>
            <TextField
                label="Owner(s)"
                value={localEnv.owners || ''}
                onChange={e => setLocalEnv(prevState => ({
                    ...prevState,
                    owners: e.target.value.length === 0 ? [] : e.target.value.split(',')
                }))}
                fullWidth
                margin="dense"
            />
            <TextField
                label="Team(s)"
                value={localEnv.teams || ''}
                onChange={e => setLocalEnv(prevState => ({
                    ...prevState,
                    teams: e.target.value.length === 0 ? [] : e.target.value.split(',')
                }))}
                fullWidth
                margin="dense"
            />
            <LocalizationProvider dateAdapter={AdapterDayjs}>
                <DatePicker
                    sx={{mt: 1, mb: 1, width: '100%'}}
                    disablePast
                    label="Expiration Date"
                    slotProps={{field: {clearable: true}}}
                    format={"DD/MM/YYYY"}
                    value={localEnv.expirationDate ? dayjs(localEnv.expirationDate) : null}
                    onChange={(newValue) => setLocalEnv(prevState => ({
                        ...prevState,
                        expirationDate: newValue ? newValue.toDate() : undefined
                    }))}/>
            </LocalizationProvider>
            <FormControl sx={{mt: 1, mb: 1}} fullWidth>
                <InputLabel>Status</InputLabel>
                <Select
                    value={localEnv.status || ''}
                    onChange={e => setLocalEnv(prev => ({...prev, status: e.target.value as EnvironmentStatus}))}
                    fullWidth
                    label="Status"
                    margin="dense"
                >
                    {ALL_STATUSES.map(status => <MenuItem key={status}
                                                          value={status}>{STATUS_MAPPING[status]}</MenuItem>)}
                </Select>
            </FormControl>
            <FormControl sx={{mt: 1, mb: 1}} fullWidth>
                <InputLabel>Type</InputLabel>
                <Select
                    value={localEnv.type || ''}
                    onChange={e => setLocalEnv(prev => ({...prev, type: e.target.value as EnvironmentType}))}
                    fullWidth
                    label="Environment Type"
                    margin="dense"
                >
                    {ALL_TYPES.map(type => <MenuItem key={type}
                                                     value={type}>{ENVIRONMENT_TYPES_MAPPING[type]}</MenuItem>)}
                </Select>
            </FormControl>
            <TextField
                label="Description"
                value={localEnv.description || ''}
                onChange={e => setLocalEnv(prev => ({...prev, description: e.target.value}))}
                fullWidth
                margin="dense"
            />
            <FormControl sx={{mt: 1, mb: 1}} fullWidth>
                <Autocomplete
                    multiple
                    options={allLabels}
                    defaultValue={localEnv.labels}
                    freeSolo
                    renderValue={(value: readonly string[], getItemProps) =>
                        value.map((option: string, index: number) => {
                            const {key, ...itemProps} = getItemProps({index});
                            return (
                                <Chip label={option} key={key} {...itemProps} />
                            );
                        })
                    }
                    renderInput={(params) => (
                        <TextField
                            {...params}
                            label="Labels"
                            placeholder="Labels"
                        />
                    )}
                    onChange={(event, value) => {
                        setLocalEnv(prev => ({...prev, labels: value}));
                    }}
                />
            </FormControl>
        </DialogContent>
        <DialogActions>
            <Button onClick={onClose} color="secondary">Close</Button>
            <Button onClick={handleSubmit} color="primary">Save Changes</Button>
        </DialogActions>
    </Dialog>
}
